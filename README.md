# About
This project is a hack-a-thon style experimental project to create simple executables for the Alfresco Content Services and Share applications. Initially this project aims to create Jetty-based executable "fat JARs" for these applications, which may form the basis of compiling these applications into native binaries at some point in the future using [GraalVM](https://www.graalvm.org/) / [SubstrateVM](https://github.com/oracle/graal/tree/master/substratevm).

# Building

This project can be built by running ``mvn clean install`` on the top level directory.

Note that the build can take quite a bit of time (10-20 minutes) due to unpacking of intermediary uber-JARs which is an expensive operation using the default Maven dependency plugin. Alternatives need to be evaluated and potentially be custom implemented to avoid this issue.

# Running

In order to run the Content Services / Share Jetty JAR the following commands can be used:

```
# -port 8080 is HTTP port on which Alfresco will be available
java -Xmx4G -Xms4G -XX:+UseG1GC -Dorg.eclipse.jetty.annotations.maxWait=120000 -jar de.axelfaust.experiment.content.services.jetty-0.0.1-SNAPSHOT-shaded.jar start -port 8080

# -port 8081 is HTTP port on which Share will be available
java -Xmx1G -Xms1G -XX:+UseG1GC -Dorg.eclipse.jetty.annotations.maxWait=120000 -jar de.axelfaust.experiment.share.jetty-0.0.1-SNAPSHOT-shaded.jar start -port 8081
```

The process for each Jetty JAR is blocking, so to start both requires two command line terminals. Each process can be properly stopped by executing the following commands in a third command line terminal:

```
# -port 8080 is HTTP port on which Alfresco is to be contacted
java -jar de.axelfaust.experiment.content.services.jetty-0.0.1-SNAPSHOT-shaded.jar stop -port 8080

# -port 8081 is HTTP port on which Share is to be contacted
java -jar de.axelfaust.experiment.share.jetty-0.0.1-SNAPSHOT-shaded.jar stop -port 8081
```

This needs to be run from the same working directory as the start command. The HTTP-based stop command is secured by a pseudo-random hash generated at startup and stored in a file ``shutdown_token``.

Runtime configuration for both Jetty JARs can be provided in a _config_ folder within the current working directory. The Content Services Jetty JAR will look for _./config/content-services/_ while the Share Jetty JAR will look for _./config/share/_. Within these configuration directories, the supported configuration files are the same as within the traditional _<tomcat>/shared/classes/_ directory, most importantly _./config/content-services/alfresco-global.properties_ and _./config/share/alfresco/web-extension/share-config-custom.xml_.

# Contents

The Content Services Jetty JAR bundles both PostgreSQL and MySQL JDBC drivers, supporting any of these databases to be used.

The Content Services Jetty JAR includes the Alfresco Office Services _vti_bin WAR and Repository AMP module.

The Content Services and Share Jetty JAR include the OOTBee Support Tools addon for improvement administration / state analysis support.