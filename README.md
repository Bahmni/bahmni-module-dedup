# bahmni-module-dedup

This module's objective is to provide specific implementation extensions. 


# Description
This module is intended for detecting duplicates and merging duplicates for patients in OpenMRS.
As of now, this module is intended to be bundled independently onto openmrs. 



### Scope of this module
- Provides a FHIR custom $match operation to detect duplicates
- Provides APIs to merge patients, and/or to specify distinct patient identities


### Extending `$match`
Other modules can narrow the candidates returned by `$match` without changing this module. Define a Spring
bean that implements `org.bahmni.module.dedup.api.service.PatientMatchFilter`:

```java
@Component
public class MyFilter implements PatientMatchFilter {
    public List<Patient> filter(List<Patient> candidates) {
        return candidates.stream().filter(...).collect(Collectors.toList());
    }
}
```

All filter beans are applied in order (use `@Order` to control it), each receiving the output of the previous
one. A filter must return a subset of the candidates it was given, preserve their order, and not modify the
input list. With no filters registered, `$match` behaves exactly as before.


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
