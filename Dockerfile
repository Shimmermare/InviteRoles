FROM maven:3-openjdk-16-slim AS maven

COPY ./pom.xml ./pom.xml
COPY ./src ./src

RUN mvn package

FROM openjdk:16-alpine

COPY --from=maven target/InviteRoles-*-jar-with-dependencies.jar /java.jar
COPY ./entry.sh /entry.sh

CMD ["/entry.sh"]
