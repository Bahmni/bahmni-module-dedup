# bahmni-module-dedup

This module's objective is to provide specific implementation extensions. 


# Description
This module is intended for detecting duplicates and merging duplicates for patients in OpenMRS.
As of now, this module is intended to be bundled independently onto openmrs. 



### Scope of this module
- Provides a FHIR custom $match operation to detect duplicates
- Provides APIs to merge patients, and/or to specify distinct patient identities


### Configuration


## Building from Source
You will need to have Java 1.8+ and Maven 2.x+ installed.  Use the command 'mvn package' to 
compile and package the module.  The .omod file will be in the omod/target folder.


## Installation
1. Build the module to produce the .omod file.
2. Use the OpenMRS Administration > Manage Modules screen to upload and install the .omod file.

If uploads are not allowed from the web (changable via a runtime property), you can drop the omod
into the ~/.OpenMRS/modules folder.  (Where ~/.OpenMRS is assumed to be the Application 
Data Directory that the running openmrs is currently using.)  After putting the file in there 
simply restart OpenMRS/tomcat and the module will be loaded and started.
