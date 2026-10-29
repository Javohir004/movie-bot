# 1-bosqich: jar yig'ish
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# dependency'lar alohida qatlamda keshlanadi, kod o'zgarganda qayta yuklanmaydi
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package

# 2-bosqich: faqat ishga tushirish uchun kerakli narsalar
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --no-create-home bot
COPY --from=build /app/target/movie-bot-1.0.0.jar app.jar
USER bot

ENTRYPOINT ["java", "-jar", "app.jar"]
