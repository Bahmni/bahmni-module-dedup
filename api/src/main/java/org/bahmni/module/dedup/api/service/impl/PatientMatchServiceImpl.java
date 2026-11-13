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
import org.apache.commons.lang3.StringUtils;
import org.bahmni.module.dedup.api.service.PatientMatchService;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.openmrs.Person;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.module.fhir2.api.translators.GenderTranslator;
import org.openmrs.module.fhir2.api.translators.PatientTranslator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component("bahmniPatientMatchService")
public class PatientMatchServiceImpl implements PatientMatchService {
	
	private static final Set<Enumerations.AdministrativeGender> ALLOWED_GENDERS = EnumSet.of(
	    Enumerations.AdministrativeGender.MALE, Enumerations.AdministrativeGender.FEMALE,
	    Enumerations.AdministrativeGender.OTHER);
	
	private final PatientService patientService;
	
	private final PersonService personService;
	
	private final GenderTranslator genderTranslator;
	
	private final PatientTranslator patientTranslator;
	
	@Autowired
	public PatientMatchServiceImpl(PatientService patientService, PersonService personService,
	    PatientTranslator patientTranslator, GenderTranslator genderTranslator) {
		this.patientService = patientService;
		this.personService = personService;
		this.genderTranslator = genderTranslator;
		this.patientTranslator = patientTranslator;
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
		
		Set<Person> similarPeople = getPersonService().getSimilarPeople(name, birthYear,
		    genderTranslator.toOpenmrsType(toMatch.getGender()));
		
		List<org.hl7.fhir.r4.model.Patient> patients = new ArrayList<>();
		for (Person person : similarPeople) {
			if (!person.getIsPatient()) {
				continue;
			}
			Optional.ofNullable(getPatientService().getPatient(person.getPersonId())).map(patientTranslator::toFhirResource)
			        .ifPresent(patients::add);
		}
		return patients;
	}
	
	private PatientService getPatientService() {
		return this.patientService;
	}
	
	private PersonService getPersonService() {
		return this.personService;
	}
	
	private void validate(org.hl7.fhir.r4.model.Patient fhirPatient) {
		HumanName name = fhirPatient.getNameFirstRep();
		if (!name.hasGiven()) {
			throw new InvalidRequestException("First name is missing");
		}
		if (StringUtils.isBlank(name.getFamily())) {
			throw new InvalidRequestException("Last name is missing");
		}
		Enumerations.AdministrativeGender gender = fhirPatient.getGender();
		if (gender == null) {
			throw new InvalidRequestException("Gender is missing");
		}
		if (!ALLOWED_GENDERS.contains(gender)) {
			throw new InvalidRequestException("Gender is not valid: expected male, female or other");
		}
	}
	
	private String joinNames(List<String> givenNames, String familyName) {
		return Stream.concat(givenNames.stream(), Stream.of(familyName)).filter(StringUtils::isNotBlank).map(String::trim)
		        .collect(Collectors.joining(" "));
	}
}
