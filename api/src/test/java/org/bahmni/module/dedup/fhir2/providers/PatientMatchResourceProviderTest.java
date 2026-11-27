/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * 			v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * 			obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * 			the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * 			Copyright (C) Thoughtworks Inc.
 */
package org.bahmni.module.dedup.fhir2.providers;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.bahmni.module.dedup.api.service.PatientMatchService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PatientMatchResourceProviderTest {
	
	@Mock
	private PatientMatchService matchService;
	
	@Mock
	private RequestDetails requestDetails;
	
	private PatientMatchResourceProvider provider;
	
	@Before
	public void setUp() {
		when(requestDetails.getFhirServerBase()).thenReturn("http://localhost/openmrs/ws/fhir2/R4");
		provider = new PatientMatchResourceProvider(matchService);
	}
	
	@Test
	public void shouldReturnSearchsetBundleWithMatchingPatients() {
		Patient request = patient("Sasirekha", "P");
		request.setGender(Enumerations.AdministrativeGender.FEMALE);
		request.setBirthDateElement(new DateType("2002-02-11"));
		Patient fhirPatient = new Patient();
		fhirPatient.setId("uuid-1");
		when(matchService.findMatches(request)).thenReturn(Collections.singletonList(fhirPatient));
		
		Bundle bundle = provider.match(request, requestDetails);
		
		assertThat(bundle.getType(), equalTo(Bundle.BundleType.SEARCHSET));
		assertThat(bundle.getTotal(), equalTo(1));
		assertThat(bundle.getEntry(), hasSize(1));
		assertThat(bundle.getEntryFirstRep().getFullUrl(), equalTo("http://localhost/openmrs/ws/fhir2/R4/Patient/uuid-1"));
		assertThat(bundle.getEntryFirstRep().getResource(), equalTo(fhirPatient));
		assertThat(bundle.getEntryFirstRep().getSearch().getMode(), equalTo(Bundle.SearchEntryMode.MATCH));
	}
	
	@Test
	public void shouldReturnEmptyBundleWhenNoMatches() {
		when(matchService.findMatches(any())).thenReturn(Collections.emptyList());
		
		Bundle bundle = provider.match(patient("Nobody", "Here"), requestDetails);
		
		assertThat(bundle.getTotal(), equalTo(0));
		assertThat(bundle.getEntry(), hasSize(0));
	}
	
	@Test(expected = InvalidRequestException.class)
	public void shouldRejectMissingResource() {
		provider.match(null, requestDetails);
	}
	
	@Test
	public void shouldDeclareResourceTypeAsPatient() {
		assertThat(provider.getResourceType().equals(Patient.class), equalTo(true));
	}
	
	@Test
	public void shouldPassUuidsAndReasonToServiceAndReturnEditedPatient() {
		Patient edited = new Patient();
		edited.setId("edited");
		when(matchService.recordNonDuplicates("edited", Arrays.asList("t1", "t2"), "because")).thenReturn(edited);
		
		Patient response = provider.recordNonDuplicate(new IdType("Patient/edited"),
		    Arrays.asList(new IdType("t1"), new IdType("t2")), new StringType("because"));
		
		assertThat(response, equalTo(edited));
	}
	
	@Test
	public void shouldPassNullReasonWhenReasonIsAbsent() {
		when(matchService.recordNonDuplicates(any(), anyList(), isNull())).thenReturn(new Patient());
		
		provider.recordNonDuplicate(new IdType("Patient/edited"), Collections.singletonList(new IdType("t1")), null);
		
		verify(matchService).recordNonDuplicates("edited", Collections.singletonList("t1"), null);
	}
	
	@Test
	public void shouldBindSampleRecordRequestParametersAsIdsAndString() {
		String sampleRequest = "{\"resourceType\": \"Parameters\", \"parameter\": ["
		        + "{\"name\": \"targetPatientId\", \"valueId\": \"pat-98765\"},"
		        + "{\"name\": \"targetPatientId\", \"valueId\": \"pat-55443\"},"
		        + "{\"name\": \"targetPatientId\", \"valueId\": \"pat-11223\"},"
		        + "{\"name\": \"reason\", \"valueString\": \"User verified all listed candidates are distinct individuals.\"}]}";
		
		Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, sampleRequest);
		
		List<Type> targets = parameters.getParameters("targetPatientId");
		targets.forEach(target -> assertThat(target, instanceOf(IdType.class)));
		assertThat(targets.stream().map(target -> ((IdType) target).getIdPart()).collect(Collectors.toList()),
		    contains("pat-98765", "pat-55443", "pat-11223"));
		assertThat(parameters.getParameter("reason"), instanceOf(StringType.class));
		assertThat(((StringType) parameters.getParameter("reason")).getValue(),
		    equalTo("User verified all listed candidates are distinct individuals."));
	}
	
	private Patient patient(String given, String family) {
		Patient patient = new Patient();
		patient.addName().addGiven(given).setFamily(family);
		patient.setGender(Enumerations.AdministrativeGender.MALE);
		return patient;
	}
}
