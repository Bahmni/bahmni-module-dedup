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
import org.bahmni.module.dedup.api.dao.PatientDuplicateSuppressionDao;
import org.bahmni.module.dedup.api.model.PatientDuplicateSuppression;
import org.bahmni.module.dedup.api.service.PatientMatchFilter;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.User;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.api.translators.PatientTranslator;
import org.openmrs.module.fhir2.api.translators.impl.GenderTranslatorImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PatientMatchServiceImplTest {
	
	@Mock
	private PersonService personService;
	
	@Mock
	private PatientService patientService;
	
	@Mock
	private PatientTranslator patientTranslator;
	
	@Mock
	private PatientDuplicateSuppressionDao suppressionDao;
	
	private MockedStatic<Context> context;
	
	private User user;
	
	private final List<Person> candidatePeople = new ArrayList<>();
	
	private PatientMatchServiceImpl service;
	
	@Before
	public void setUp() {
		user = new User();
		context = Mockito.mockStatic(Context.class);
		context.when(Context::getAuthenticatedUser).thenReturn(user);
		service = new PatientMatchServiceImpl(patientService, personService, patientTranslator, new GenderTranslatorImpl(),
		        suppressionDao);
		givenPatientWithUuid("edited", 10);
		givenPatientWithUuid("target-20", 20);
		givenPatientWithUuid("target-30", 30);
	}
	
	@After
	public void tearDown() {
		context.close();
	}
	
	@Test
	public void shouldJoinNamesAndConvertGenderAndYear() {
		when(personService.getSimilarPeople(any(), any(), any())).thenReturn(Collections.emptySet());
		
		service.findMatches(
		    fhirPatient(Arrays.asList("Sasirekha", "Devi"), "P", Enumerations.AdministrativeGender.FEMALE, "2002-02-11"));
		
		verify(personService).getSimilarPeople("Sasirekha Devi P", 2002, "F");
	}
	
	@Test
	public void shouldConvertMaleAndOtherGender() {
		when(personService.getSimilarPeople(any(), any(), any())).thenReturn(Collections.emptySet());
		
		service.findMatches(
		    fhirPatient(Collections.singletonList("Akash"), "B", Enumerations.AdministrativeGender.MALE, null));
		service.findMatches(fhirPatient(Collections.singletonList("A"), "B", Enumerations.AdministrativeGender.OTHER, null));
		
		verify(personService).getSimilarPeople("Akash B", null, "M");
		verify(personService).getSimilarPeople("A B", null, "O");
	}
	
	@Test
	public void shouldPassNullYearWhenBirthDateIsAbsent() {
		when(personService.getSimilarPeople(any(), any(), any())).thenReturn(Collections.emptySet());
		
		service.findMatches(fhirPatient(Collections.singletonList("A"), "B", Enumerations.AdministrativeGender.MALE, null));
		
		verify(personService).getSimilarPeople("A B", null, "M");
	}
	
	@Test
	public void shouldSkipPeopleWhoAreNotPatientsAndReturnPatients() {
		Person patientPerson = person(1, true);
		Person nonPatientPerson = person(2, false);
		org.hl7.fhir.r4.model.Patient fhirPatient = fhirPatient(Collections.singletonList("Raghu"), "Jetley",
		    Enumerations.AdministrativeGender.MALE, "1995");
		when(personService.getSimilarPeople(any(), any(), any()))
		        .thenReturn(new LinkedHashSet<>(Arrays.asList(patientPerson, nonPatientPerson)));
		when(patientService.getPatient(1)).thenReturn(new Patient());
		when(patientTranslator.toFhirResource(any(Patient.class))).thenReturn(fhirPatient);
		List<org.hl7.fhir.r4.model.Patient> result = service
		        .findMatches(fhirPatient(Collections.singletonList("A"), "B", Enumerations.AdministrativeGender.MALE, null));
		assertThat(result, contains(fhirPatient));
		verify(patientService, never()).getPatient(2);
	}
	
	@Test
	public void shouldReturnEmptyWhenNoSimilarPersonIsAPatient() {
		Person first = person(1, false);
		Person second = person(2, false);
		when(personService.getSimilarPeople(any(), any(), any()))
		        .thenReturn(new LinkedHashSet<>(Arrays.asList(first, second)));
		
		List<org.hl7.fhir.r4.model.Patient> result = service
		        .findMatches(fhirPatient(Collections.singletonList("A"), "B", Enumerations.AdministrativeGender.MALE, null));
		
		assertThat(result, empty());
		verify(patientService, never()).getPatient(any());
	}
	
	@Test
	public void shouldDropPatientsRemovedByFilterAndNotTranslateThem() {
		Patient kept = new Patient();
		Patient dropped = new Patient();
		org.hl7.fhir.r4.model.Patient keptFhir = fhirPatient(Collections.singletonList("Raghu"), "Jetley",
		    Enumerations.AdministrativeGender.MALE, "1995");
		Person keptPerson = person(1, true);
		Person droppedPerson = person(2, true);
		when(personService.getSimilarPeople(any(), any(), any()))
		        .thenReturn(new LinkedHashSet<>(Arrays.asList(keptPerson, droppedPerson)));
		when(patientService.getPatient(1)).thenReturn(kept);
		when(patientService.getPatient(2)).thenReturn(dropped);
		when(patientTranslator.toFhirResource(kept)).thenReturn(keptFhir);
		service.setFilters(Collections
		        .singletonList(candidates -> candidates.stream().filter(p -> p != dropped).collect(Collectors.toList())));
		
		List<org.hl7.fhir.r4.model.Patient> result = service
		        .findMatches(fhirPatient(Collections.singletonList("A"), "B", Enumerations.AdministrativeGender.MALE, null));
		
		assertThat(result, contains(keptFhir));
		verify(patientTranslator, never()).toFhirResource(dropped);
	}
	
	@Test
	public void shouldChainFiltersSoEachReceivesThePreviousOutput() {
		Patient first = new Patient();
		Patient second = new Patient();
		Patient third = new Patient();
		org.hl7.fhir.r4.model.Patient secondFhir = fhirPatient(Collections.singletonList("Raghu"), "Jetley",
		    Enumerations.AdministrativeGender.MALE, "1995");
		Person firstPerson = person(1, true);
		Person secondPerson = person(2, true);
		Person thirdPerson = person(3, true);
		when(personService.getSimilarPeople(any(), any(), any()))
		        .thenReturn(new LinkedHashSet<>(Arrays.asList(firstPerson, secondPerson, thirdPerson)));
		when(patientService.getPatient(1)).thenReturn(first);
		when(patientService.getPatient(2)).thenReturn(second);
		when(patientService.getPatient(3)).thenReturn(third);
		when(patientTranslator.toFhirResource(second)).thenReturn(secondFhir);
		PatientMatchFilter dropFirst = candidates -> candidates.stream().filter(p -> p != first)
		        .collect(Collectors.toList());
		PatientMatchFilter dropThird = candidates -> {
			assertThat(candidates, contains(second, third));
			return candidates.stream().filter(p -> p != third).collect(Collectors.toList());
		};
		service.setFilters(Arrays.asList(dropFirst, dropThird));
		
		List<org.hl7.fhir.r4.model.Patient> result = service
		        .findMatches(fhirPatient(Collections.singletonList("A"), "B", Enumerations.AdministrativeGender.MALE, null));
		
		assertThat(result, contains(secondFhir));
	}
	
	@Test
	public void shouldReturnEmptyWhenFilterRemovesEveryCandidate() {
		Person onlyPerson = person(1, true);
		when(personService.getSimilarPeople(any(), any(), any()))
		        .thenReturn(new LinkedHashSet<>(Collections.singletonList(onlyPerson)));
		when(patientService.getPatient(1)).thenReturn(new Patient());
		service.setFilters(Collections.singletonList(candidates -> Collections.emptyList()));
		
		List<org.hl7.fhir.r4.model.Patient> result = service
		        .findMatches(fhirPatient(Collections.singletonList("A"), "B", Enumerations.AdministrativeGender.MALE, null));
		
		assertThat(result, empty());
		verify(patientTranslator, never()).toFhirResource(any(Patient.class));
	}
	
	@Test
	public void shouldReturnEmptyWhenNoSimilarPeople() {
		when(personService.getSimilarPeople(any(), any(), any())).thenReturn(Collections.emptySet());
		
		assertThat(service.findMatches(
		    fhirPatient(Collections.singletonList("A"), "B", Enumerations.AdministrativeGender.MALE, null)), empty());
	}
	
	@Test
	public void shouldRejectMissingFirstName() {
		org.hl7.fhir.r4.model.Patient request = new org.hl7.fhir.r4.model.Patient();
		request.addName().setFamily("B");
		request.setGender(Enumerations.AdministrativeGender.FEMALE);
		
		assertThat(rejectionMessage(request), containsString("First name is missing"));
		verify(personService, never()).getSimilarPeople(any(), any(), any());
	}
	
	@Test
	public void shouldRejectMissingLastName() {
		org.hl7.fhir.r4.model.Patient request = new org.hl7.fhir.r4.model.Patient();
		request.addName().addGiven("A");
		request.setGender(Enumerations.AdministrativeGender.FEMALE);
		
		assertThat(rejectionMessage(request), containsString("Last name is missing"));
	}
	
	@Test
	public void shouldRejectMissingGender() {
		org.hl7.fhir.r4.model.Patient request = new org.hl7.fhir.r4.model.Patient();
		request.addName().addGiven("A").setFamily("B");
		
		assertThat(rejectionMessage(request), containsString("Gender is missing"));
	}
	
	@Test
	public void shouldRejectUnknownGenderAsNotValid() {
		org.hl7.fhir.r4.model.Patient request = fhirPatient(Collections.singletonList("A"), "B",
		    Enumerations.AdministrativeGender.UNKNOWN, null);
		
		assertThat(rejectionMessage(request), containsString("Gender is not valid"));
		verify(personService, never()).getSimilarPeople(any(), any(), any());
	}
	
	@Test
	public void shouldRecordOneNonDuplicatePairPerTarget() {
		service.recordNonDuplicates("edited", Arrays.asList("target-20", "target-30"), "distinct");
		
		List<PatientDuplicateSuppression> saved = savedRows(2);
		assertThat(saved.get(0).getPatientIdA(), equalTo(10));
		assertThat(saved.get(0).getPatientIdB(), equalTo(20));
		assertThat(saved.get(1).getPatientIdA(), equalTo(10));
		assertThat(saved.get(1).getPatientIdB(), equalTo(30));
	}
	
	@Test
	public void shouldStoreSmallerPatientIdFirstRegardlessOfRequestOrder() {
		givenPatientWithUuid("edited-high", 50);
		
		service.recordNonDuplicates("edited-high", Collections.singletonList("target-20"), null);
		
		PatientDuplicateSuppression saved = savedRows(1).get(0);
		assertThat(saved.getPatientIdA(), equalTo(20));
		assertThat(saved.getPatientIdB(), equalTo(50));
		verify(suppressionDao).findExistingSuppressions(50, Collections.singleton(20));
	}
	
	@Test
	public void shouldSetReasonCreatorUuidAndDateOnSavedRow() {
		service.recordNonDuplicates("edited", Collections.singletonList("target-20"), "User verified");
		
		PatientDuplicateSuppression saved = savedRows(1).get(0);
		assertThat(saved.getReason(), equalTo("User verified"));
		assertThat(saved.getCreator(), equalTo(user));
		assertThat(saved.getUuid(), notNullValue());
		assertThat(saved.getDateCreated(), notNullValue());
	}
	
	@Test
	public void shouldAllowMissingReason() {
		service.recordNonDuplicates("edited", Collections.singletonList("target-20"), null);
		
		assertThat(savedRows(1).get(0).getReason(), equalTo(null));
	}
	
	@Test
	public void shouldSkipNonDuplicatePairThatIsAlreadyRecorded() {
		PatientDuplicateSuppression existing = new PatientDuplicateSuppression();
		existing.setPatientIdA(10);
		existing.setPatientIdB(20);
		when(suppressionDao.findExistingSuppressions(10, new LinkedHashSet<>(Arrays.asList(20, 30))))
		        .thenReturn(Collections.singletonList(existing));
		
		service.recordNonDuplicates("edited", Arrays.asList("target-20", "target-30"), null);
		
		assertThat(savedRows(1).get(0).getPatientIdB(), equalTo(30));
	}
	
	@Test
	public void shouldSkipPairAlreadyRecordedInTheOppositeDirection() {
		when(suppressionDao.findExistingSuppressions(20, Collections.singleton(10)))
		        .thenReturn(Collections.singletonList(suppression(10, 20)));
		
		service.recordNonDuplicates("target-20", Collections.singletonList("edited"), null);
		
		verify(suppressionDao, never()).save(any());
	}
	
	@Test
	public void shouldOnlyRecordNewTargetsWhenSomeAreAlreadyRecordedInTheOppositeDirection() {
		when(suppressionDao.findExistingSuppressions(20, new LinkedHashSet<>(Arrays.asList(10, 30))))
		        .thenReturn(Collections.singletonList(suppression(10, 20)));
		
		service.recordNonDuplicates("target-20", Arrays.asList("edited", "target-30"), null);
		
		PatientDuplicateSuppression saved = savedRows(1).get(0);
		assertThat(saved.getPatientIdA(), equalTo(20));
		assertThat(saved.getPatientIdB(), equalTo(30));
	}
	
	@Test
	public void shouldRecordRepeatedTargetOnlyOnce() {
		service.recordNonDuplicates("edited", Arrays.asList("target-20", "target-20"), null);
		
		savedRows(1);
	}
	
	@Test
	public void shouldReturnEditedPatientAsFhirResource() {
		org.hl7.fhir.r4.model.Patient fhirPatient = new org.hl7.fhir.r4.model.Patient();
		fhirPatient.setId("edited");
		when(patientTranslator.toFhirResource(any(Patient.class))).thenReturn(fhirPatient);
		
		org.hl7.fhir.r4.model.Patient result = service.recordNonDuplicates("edited", Collections.singletonList("target-20"),
		    null);
		
		assertThat(result, equalTo(fhirPatient));
	}
	
	@Test
	public void shouldReturnNotFoundWhenEditedPatientDoesNotExist() {
		try {
			service.recordNonDuplicates("missing", Collections.singletonList("target-20"), null);
			throw new AssertionError("Expected ResourceNotFoundException");
		}
		catch (ResourceNotFoundException e) {
			assertThat(e.getMessage(), containsString("missing"));
		}
		verify(suppressionDao, never()).save(any());
	}
	
	@Test
	public void shouldSaveNothingWhenALaterTargetDoesNotExist() {
		try {
			service.recordNonDuplicates("edited", Arrays.asList("target-20", "missing"), null);
			throw new AssertionError("Expected ResourceNotFoundException");
		}
		catch (ResourceNotFoundException e) {
			assertThat(e.getMessage(), containsString("missing"));
		}
		verify(suppressionDao, never()).save(any());
		verify(suppressionDao, never()).findExistingSuppressions(any(), any());
	}
	
	@Test
	public void shouldRejectPatientAsItsOwnNonDuplicateTarget() {
		assertRecordRejected(PatientMatchServiceImpl.ERR_SELF_TARGET, "edited", Arrays.asList("target-20", "edited"), null);
	}
	
	@Test
	public void shouldRejectEmptyNonDuplicateTargets() {
		assertRecordRejected(PatientMatchServiceImpl.ERR_NO_TARGETS, "edited", Collections.emptyList(), null);
	}
	
	@Test
	public void shouldRejectNullNonDuplicateTargets() {
		assertRecordRejected(PatientMatchServiceImpl.ERR_NO_TARGETS, "edited", null, null);
	}
	
	@Test
	public void shouldRejectReasonLongerThanLimit() {
		String tooLong = new String(new char[PatientMatchServiceImpl.MAX_REASON_LENGTH + 1]).replace('\0', 'x');
		
		assertRecordRejected(PatientMatchServiceImpl.ERR_REASON_TOO_LONG, "edited", Collections.singletonList("target-20"),
		    tooLong);
	}
	
	@Test
	public void shouldAcceptReasonAtTheLimit() {
		String atLimit = new String(new char[PatientMatchServiceImpl.MAX_REASON_LENGTH]).replace('\0', 'x');
		
		service.recordNonDuplicates("edited", Collections.singletonList("target-20"), atLimit);
		
		assertThat(savedRows(1).get(0).getReason(), equalTo(atLimit));
	}
	
	@Test
	public void shouldRejectBlankPatientIdsWhenRecordingNonDuplicates() {
		assertRecordRejected(PatientMatchServiceImpl.ERR_BLANK_PATIENT_ID, " ", Collections.singletonList("target-20"),
		    null);
		assertRecordRejected(PatientMatchServiceImpl.ERR_BLANK_PATIENT_ID, "edited", Collections.singletonList(""), null);
	}
	
	@Test
	public void shouldDropCandidatesRecordedAsNonDuplicatesOfTheEditedPatient() {
		Patient kept = givenCandidate(30);
		givenCandidate(20);
		org.hl7.fhir.r4.model.Patient keptFhir = new org.hl7.fhir.r4.model.Patient();
		when(patientTranslator.toFhirResource(kept)).thenReturn(keptFhir);
		when(suppressionDao.findExistingSuppressions(eq(10), any()))
		        .thenReturn(Collections.singletonList(suppression(10, 20)));
		
		List<org.hl7.fhir.r4.model.Patient> result = service.findMatches(requestFor("edited"));
		
		assertThat(result, contains(keptFhir));
		verify(patientTranslator, never()).toFhirResource(argThat((Patient p) -> p.getPatientId() == 20));
	}
	
	@Test
	public void shouldNotConsultRecordedNonDuplicatesWhenRequestHasNoId() {
		givenCandidate(20);
		
		service.findMatches(fhirPatient(Collections.singletonList("A"), "B", Enumerations.AdministrativeGender.MALE, null));
		
		verify(suppressionDao, never()).findExistingSuppressions(any(), any());
	}
	
	@Test
	public void shouldKeepAllCandidatesWhenEditedPatientIdIsUnknown() {
		Patient candidate = givenCandidate(20);
		org.hl7.fhir.r4.model.Patient candidateFhir = new org.hl7.fhir.r4.model.Patient();
		when(patientTranslator.toFhirResource(candidate)).thenReturn(candidateFhir);
		
		List<org.hl7.fhir.r4.model.Patient> result = service.findMatches(requestFor("unknown"));
		
		assertThat(result, contains(candidateFhir));
		verify(suppressionDao, never()).findExistingSuppressions(any(), any());
	}
	
	@Test
	public void shouldNotConsultRecordedNonDuplicatesWhenThereAreNoCandidates() {
		when(personService.getSimilarPeople(any(), any(), any())).thenReturn(Collections.emptySet());
		
		service.findMatches(requestFor("edited"));
		
		verify(suppressionDao, never()).findExistingSuppressions(any(), any());
	}
	
	@Test
	public void shouldApplyRecordedNonDuplicatesBeforeFilters() {
		Patient kept = givenCandidate(30);
		givenCandidate(20);
		when(suppressionDao.findExistingSuppressions(eq(10), any()))
		        .thenReturn(Collections.singletonList(suppression(10, 20)));
		service.setFilters(Collections.singletonList(candidates -> {
			assertThat(candidates, contains(kept));
			return candidates;
		}));
		
		service.findMatches(requestFor("edited"));
	}
	
	private org.hl7.fhir.r4.model.Patient requestFor(String uuid) {
		org.hl7.fhir.r4.model.Patient request = fhirPatient(Collections.singletonList("A"), "B",
		    Enumerations.AdministrativeGender.MALE, null);
		request.setId(uuid);
		return request;
	}
	
	private Patient givenCandidate(int patientId) {
		Patient candidate = new Patient(patientId);
		when(patientService.getPatient(patientId)).thenReturn(candidate);
		candidatePeople.add(person(patientId, true));
		when(personService.getSimilarPeople(any(), any(), any())).thenReturn(new LinkedHashSet<>(candidatePeople));
		return candidate;
	}
	
	private PatientDuplicateSuppression suppression(int patientIdA, int patientIdB) {
		PatientDuplicateSuppression suppression = new PatientDuplicateSuppression();
		suppression.setPatientIdA(patientIdA);
		suppression.setPatientIdB(patientIdB);
		return suppression;
	}
	
	private void assertRecordRejected(String expectedMessage, String patientUuid, List<String> targets, String reason) {
		try {
			service.recordNonDuplicates(patientUuid, targets, reason);
		}
		catch (InvalidRequestException e) {
			assertThat(e.getMessage(), containsString(expectedMessage));
			verify(suppressionDao, never()).save(any());
			return;
		}
		throw new AssertionError("Expected InvalidRequestException");
	}
	
	private void givenPatientWithUuid(String uuid, int patientId) {
		when(patientService.getPatientByUuid(uuid)).thenReturn(new Patient(patientId));
	}
	
	private List<PatientDuplicateSuppression> savedRows(int expectedCount) {
		ArgumentCaptor<PatientDuplicateSuppression> captor = ArgumentCaptor.forClass(PatientDuplicateSuppression.class);
		verify(suppressionDao, times(expectedCount)).save(captor.capture());
		return captor.getAllValues();
	}
	
	private String rejectionMessage(org.hl7.fhir.r4.model.Patient request) {
		try {
			service.findMatches(request);
		}
		catch (InvalidRequestException e) {
			return e.getMessage();
		}
		throw new AssertionError("Expected InvalidRequestException");
	}
	
	private org.hl7.fhir.r4.model.Patient fhirPatient(List<String> givenNames, String familyName,
	        Enumerations.AdministrativeGender gender, String birthDate) {
		org.hl7.fhir.r4.model.Patient patient = new org.hl7.fhir.r4.model.Patient();
		HumanName name = patient.addName();
		givenNames.forEach(name::addGiven);
		name.setFamily(familyName);
		patient.setGender(gender);
		if (birthDate != null) {
			patient.setBirthDateElement(new DateType(birthDate));
		}
		return patient;
	}
	
	private Person person(int id, boolean isPatient) {
		Person person = Mockito.mock(Person.class);
		when(person.getPersonId()).thenReturn(id);
		when(person.getIsPatient()).thenReturn(isPatient);
		return person;
	}
}
