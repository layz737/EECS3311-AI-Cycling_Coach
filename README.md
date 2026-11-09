# AI Cycling Training Coach

EECS 3311 Fall 2026 Course Project

AI Cycling Training Coach is a Java application for recreational and amateur cyclists. It combines deterministic cycling-data analysis with an AI/LLM-based training assistant to help riders organize training, analyze rides, track fitness progress, and receive personalized training recommendations.

## Project Status

### Stage 1 – Software Design

Completed.

Stage 1 includes:

- Project requirements and feature specifications
- UML class diagram
- Use-case diagram and use-case descriptions
- Sequence diagrams
- Design-pattern planning
- Feature-to-design traceability

### Stage 2 – AI-Human Collaborative Implementation

Implemented and functionally tested.

The application currently includes:

- Java Swing graphical user interface
- Command-line interface
- Rider profile persistence
- Garmin CSV ride-history import
- Ride-history persistence
- Fitness-progress persistence
- Training-plan persistence
- Deterministic ride analysis features
- DeepSeek API integration
- AI-assisted ride analysis and training guidance

## Features

The application implements the 11 features planned in Stage 1:

1. **Rider Profile Setup**
   Create and update rider information including weight, FTP, heart-rate values, available training days, targets, and training goal.
2. **Ride Data Import**
   Import multiple cycling activities from a Garmin activities-history CSV file.
3. **AI Ride Analysis**
   Analyze a selected ride using rider-profile and ride-summary data.
4. **Weekly Training Plan Generation**
   Generate a structured one-week training plan based on the rider's profile, recent rides, and selected goal.
5. **AI Chat**
   Ask cycling-training questions using the rider's current profile and recent training history as context.
6. **Progress Tracking**
   Track changes in FTP and body weight and calculate progress toward rider goals.
7. **Weakness / Improvement Detection**
   Use AI to identify possible training weaknesses and improvement opportunities from recent ride data.
8. **Weekly Training Summary**
   Generate an AI-assisted summary of the rider's recent training week.
9. **Ride Comparison**
   Deterministically compare two imported rides across distance, duration, speed, heart rate, power, and cadence.
10. **Workout Calendar**
    Display planned workouts and completed rides in a Monday-to-Sunday weekly view.
11. **Personal Bests**
    Detect the longest distance, highest average power, highest maximum power, and fastest average speed from rides in the past month.

## Technologies

- Java 17
- Java Swing
- Eclipse
- Git and GitHub
- DeepSeek API
- Garmin CSV activity exports
- Java `HttpClient`
- Local file-based persistence

No external Java libraries are required.

## Design Patterns

The implementation uses the five design patterns planned in Stage 1:

- **Adapter**
  `LLMClient`, `DeepSeekAdapter`, and `DeepSeekService` isolate the application from the specific LLM provider implementation.
- **Facade**
  `CyclingCoachController` provides the GUI and CLI with a single entry point to application functionality.
- **Observer**
  `RiderProfile` and `TrainingPlan` notify observers such as `ProgressTracker` and `WorkoutCalendarService` when relevant data
