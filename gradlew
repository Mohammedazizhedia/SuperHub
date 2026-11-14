#!/bin/sh
# Built with BFB.AI
# Gradle Wrapper for Linux/macOS

APP_HOME=$(cd "${0%/*}" && pwd -P)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
else
    JAVACMD=java
fi

exec "$JAVACMD" \
  -Xmx64m -Xms64m \
  -Dorg.gradle.appname=gradlew \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
