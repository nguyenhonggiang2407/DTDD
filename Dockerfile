FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system --gid 10001 yang && useradd --system --uid 10001 --gid yang --no-create-home yang && mkdir -p /app/data /probe/vn/yang/slots && chown -R yang:yang /app
COPY --from=build /build/target/yangslots.jar /app/yangslots.jar
COPY --from=build /build/target/classes/vn/yang/slots/HealthProbe.class /probe/vn/yang/slots/HealthProbe.class
ENV YANGSLOTS_BIND=0.0.0.0 PORT=3150 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
USER yang
EXPOSE 3150
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s CMD ["java","-cp","/probe","vn.yang.slots.HealthProbe"]
CMD ["java","-jar","/app/yangslots.jar"]

