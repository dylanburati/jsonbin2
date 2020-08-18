FROM openjdk:11-jre

ENV APPLICATION_USER jsonbin
RUN adduser $APPLICATION_USER

RUN mkdir /app
RUN chown -R $APPLICATION_USER /app

USER $APPLICATION_USER

COPY ./build/libs/jsonbin2.jar /app/jsonbin2.jar
WORKDIR /app

ENV JDK_JAVA_OPTIONS -Xms1g -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+UseStringDeduplication
CMD ["java", "-server", "-jar", "jsonbin2.jar"]
