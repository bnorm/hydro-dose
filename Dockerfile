# build contianer
FROM gradle:6.8.1-jdk11 AS build
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dkotlin.incremental=false"
ENV APP_HOME=/usr/app/
WORKDIR $APP_HOME

COPY build.gradle.kts settings.gradle.kts $APP_HOME
RUN gradle --console=plain

COPY . $APP_HOME
RUN gradle --console=plain distTar

# actual container
FROM adoptopenjdk:11-jre-hotspot
ENV APP_HOME=/usr/app/
WORKDIR $APP_HOME

COPY --from=build $APP_HOME/build/distributions/ .
RUN tar -xvf *.tar --strip 1

#EXPOSE 8080
ENTRYPOINT ["bin/hydro-dose"]
