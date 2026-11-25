/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * 			v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * 			obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * 			the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * 			Copyright (C) Thoughtworks Inc.
 */
package org.bahmni.module.dedup.api.service.impl;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.bahmni.module.dedup.api.dao.PatientDuplicateSuppressionDao;
import org.bahmni.module.dedup.api.model.PatientDuplicateSuppression;
import org.bahmni.module.dedup.api.service.PatientMatchFilter;
import org.bahmni.module.dedup.api.service.PatientMatchService;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.openmrs.Person;
import org.openmrs.User;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.api.translators.GenderTranslator;
import org.openmrs.module.fhir2.api.translators.PatientTranslator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component("bahmniPatientMatchService")
public class PatientMatchServiceImpl implements PatientMatchService {
	
	private static final Set<Enumerations.AdministrativeGender> ALLOWED_GENDERS = EnumSet.of(
	    Enumerations.AdministrativeGender.MALE, Enumerations.AdministrativeGender.FEMALE,
	    Enumerations.AdministrativeGender.OTHER);
	
	public static final String ERR_MISSING_FIRST_NAME = "First name is missing";
	
	public static final String ERR_MISSING_LAST_NAME = "Last name is missing";
	
	public static final String ERROR_MISSING_GENDER = "Gender is missing";
	
	public static final String ERR_INVALID_GENDER = "Gender is not valid: expected male, female or other";
	
	public static final int MAX_REASON_LENGTH = 255;
	
	public static final String ERR_NO_TARGETS = "At least one targetPatientId is required";
	
	public static final String ERR_REASON_TOO_LONG = "Reason must not exceed " + MAX_REASON_LENGTH + " characters";
	
	public static final String ERR_SELF_TARGET = "A patient cannot be recorded as a non-duplicate of itself";
	
	public static final String ERR_BLANK_PATIENT_ID = "Patient id must not be blank";
	
	private final PatientService patientService;
	
	private final PersonService personService;
	
	private final GenderTranslator genderTranslator;
	
	private final PatientTranslator patientTranslator;
	
	private final PatientDuplicateSuppressionDao suppressionDao;
	
	private List<PatientMatchFilter> filters = Collections.emptyList();
	
	@Autowired
	public PatientMatchServiceImpl(PatientService patientService, PersonService personService,
	    PatientTranslator patientTranslator, GenderTranslator genderTranslator,
	    PatientDuplicateSuppressionDao suppressionDao) {
		this.patientService = patientService;
		this.personService = personService;
		this.genderTranslator = genderTranslator;
		this.patientTranslator = patientTranslator;
		this.suppressionDao = suppressionDao;
	}
	
	@Override
	public List<Patient> findMatches(Patient toMatch) {
		validate(toMatch);
		HumanName humanName = toMatch.getNameFirstRep();
		List<String> givenNames = humanName.getGiven().stream().map(StringType::getValue).collect(Collectors.toList());
		String name = joinNames(givenNames, humanName.getFamily());
		Integer birthYear = toMatch.hasBirthDate()
		        ? toMatch.getBirthDate().toInstant().atZone(ZoneId.systemDefault()).getYear()
		        : null;
		
		Set<Person> similarPeople = personService.getSimilarPeople(name, birthYear,
		    genderTranslator.toOpenmrsType(toMatch.getGender()));
		
		List<org.openmrs.Patient> candidates = similarPeople.stream().filter(Person::getIsPatient)
		        .map(person -> patientService.getPatient(person.getPersonId())).collect(Collectors.toList());
		for (PatientMatchFilter filter : filters) {
			candidates = filter.filter(candidates);
		}
		
		return candidates.stream().map(patientTranslator::toFhirResource).collect(Collectors.toList());
	}
	
	@Override
	@Transactional
	public Patient recordNonDuplicates(String patientUuid, List<String> targetUuids, String reason) {
		if (targetUuids == null || targetUuids.isEmpty()) {
			throw new InvalidRequestException(ERR_NO_TARGETS);
		}
		if (reason != null && reason.length() > MAX_REASON_LENGTH) {
			throw new InvalidRequestException(ERR_REASON_TOO_LONG);
		}
		
		org.openmrs.Patient patient = requirePatient(patientUuid);
		Integer primaryId = patient.getPatientId();
		
		Set<Integer> targetIds = new LinkedHashSet<>();
		for (String targetUuid : new LinkedHashSet<>(targetUuids)) {
			org.openmrs.Patient target = requirePatient(targetUuid);
			if (target.getPatientId().equals(primaryId)) {
				throw new InvalidRequestException(ERR_SELF_TARGET);
			}
			targetIds.add(target.getPatientId());
		}
		
		Set<Integer> alreadySuppressedIds = suppressionDao.findExistingSuppressions(primaryId, targetIds).stream()
		        .map(s -> s.getPatientIdA().equals(primaryId) ? s.getPatientIdB() : s.getPatientIdA())
		        .collect(Collectors.toSet());
		
		User currentUser = Context.getAuthenticatedUser();
		Date now = new Date();
		
		for (Integer targetId : targetIds) {
			if (alreadySuppressedIds.contains(targetId)) {
				continue;
			}
			PatientDuplicateSuppression suppression = new PatientDuplicateSuppression();
			suppression.setPatientIdA(Math.min(primaryId, targetId));
			suppression.setPatientIdB(Math.max(primaryId, targetId));
			suppression.setReason(reason);
			suppression.setCreator(currentUser);
			suppression.setDateCreated(now);
			suppressionDao.save(suppression);
		}
		
		return patientTranslator.toFhirResource(patient);
	}
	
	@Autowired(required = false)
	public void setFilters(List<PatientMatchFilter> filters) {
		this.filters = filters;
	}
	
	private org.openmrs.Patient requirePatient(String uuid) {
		if (StringUtils.isBlank(uuid)) {
			throw new InvalidRequestException(ERR_BLANK_PATIENT_ID);
		}
		org.openmrs.Patient patient = patientService.getPatientByUuid(uuid);
		if (patient == null) {
			throw new ResourceNotFoundException("Patient not found: " + uuid);
		}
		return patient;
	}
	
	private void validate(org.hl7.fhir.r4.model.Patient fhirPatient) {
		HumanName name = fhirPatient.getNameFirstRep();
		if (!name.hasGiven()) {
			throw new InvalidRequestException(ERR_MISSING_FIRST_NAME);
		}
		if (StringUtils.isBlank(name.getFamily())) {
			throw new InvalidRequestException(ERR_MISSING_LAST_NAME);
		}
		Enumerations.AdministrativeGender gender = fhirPatient.getGender();
		if (gender == null) {
			throw new InvalidRequestException(ERROR_MISSING_GENDER);
		}
		if (!ALLOWED_GENDERS.contains(gender)) {
			throw new InvalidRequestException(ERR_INVALID_GENDER);
		}
	}
	
	private String joinNames(List<String> givenNames, String familyName) {
		return Stream.concat(givenNames.stream(), Stream.of(familyName)).filter(StringUtils::isNotBlank).map(String::trim)
		        .collect(Collectors.joining(" "));
	}
}
