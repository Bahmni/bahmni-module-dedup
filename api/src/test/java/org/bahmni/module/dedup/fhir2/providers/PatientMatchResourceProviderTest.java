/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * 			v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * 			obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * 			the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * 			Copyright (C) Thoughtworks Inc.
 */
package org.bahmni.module.dedup.fhir2.providers;

import ca.uhn.fhir.rest.api.server.IBundleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientMatchResourceProviderTest {
	
	@Mock
	private IBundleProvider bundleProvider;
	
	private PatientMatchResourceProvider provider;
	
	@BeforeEach
	void setUp() {
		provider = new PatientMatchResourceProvider();
	}
	
}
