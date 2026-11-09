/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.bahmni.module.dedup.fhir2.providers;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.openmrs.module.fhir2.api.annotations.R4Provider;
import org.springframework.stereotype.Component;

@Component("bahmniPatientMatchProvider")
@R4Provider
public class PatientMatchResourceProvider implements IResourceProvider {
	
	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return Patient.class;
	}
	
	@Operation(name = "$match", idempotent = false)
	public Bundle match(@OperationParam(name = "resource") Patient resource, RequestDetails requestDetails) {
		if (resource == null) {
			throw new InvalidRequestException("Parameter 'resource' with a Patient is required");
		}
		
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.setTotal(1);
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setFullUrl(requestDetails.getFhirServerBase() + "/Patient/test-1");
		entry.setResource(resource); //dummy
		entry.getSearch().setMode(Bundle.SearchEntryMode.MATCH);
		
		return bundle;
	}
}
