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
import java.util.EnumSet;
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
		
		Set<Person> similarPeople = personService.getSimilarPeople(name, birthYear,
		    genderTranslator.toOpenmrsType(toMatch.getGender()));
		
		return similarPeople.stream().filter(Person::getIsPatient)
		        .map(person -> patientService.getPatient(person.getPersonId())).map(patientTranslator::toFhirResource)
		        .collect(Collectors.toList());
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
