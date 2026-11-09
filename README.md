- # AI Cycling Training Coach
  
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
    `RiderProfile` and `TrainingPlan` notify observers such as `ProgressTracker` and `WorkoutCalendarService` when relevant data changes.
  - **Strategy**
    Training-plan generation selects between goal-specific strategies such as FTP improvement and weight-loss training.
  - **MVC**
    The GUI and CLI act as views, `CyclingCoachController` acts as the controller, and the models/services contain application data and business logic.
  
  ## Repository Structure
  
  ```
  Stage1/       Stage 1 design report and UML diagrams
  src/          Java application source code
  sample_data/  Safe sample Garmin ride data for testing/demo
  test/         Test directory
  docs/         Project documentation and reports
  data/         Runtime application data (created locally and ignored by Git)
  ```
  
  Main source packages under `src/` include:
  
  ```
  ai/           LLM interface, DeepSeek adapter, and API service
  controller/   Application controller/facade
  model/        Rider, workout, plan, and fitness models
  observer/     Observer-pattern interfaces
  service/      Application and AI feature services
  storage/      Local persistence classes
  strategy/     Training-plan Strategy implementations
  ui/           Swing GUI and command-line interface
  Main.java     Application entry point
  ```
  
  ## Requirements
  
  - Java Development Kit 17 or later
  - Eclipse or another Java-compatible IDE
  - Internet connection for DeepSeek-powered features
  - DeepSeek API key for AI features
  
  Deterministic features can still run without a DeepSeek API key.
  
  ## DeepSeek API Configuration
  
  The application reads the API key from the process environment variable:
  
  ```
  DEEPSEEK_API_KEY
  ```
  
  A safe example file is included:
  
  ```
  .env.example
  ```
  
  The real API key must never be committed to GitHub.
  
  The repository's `.gitignore` excludes `.env` files and runtime data.
  
  ### Eclipse Setup
  
  In Eclipse:
  
  1. Open **Run → Run Configurations**.
  2. Select the Java Application configuration for `Main`.
  3. Open the **Environment** tab.
  4. Add a new environment variable:
     - Name: `DEEPSEEK_API_KEY`
     - Value: your DeepSeek API key
  5. Apply the configuration.
  
  The application does not automatically load `.env`; the environment variable must be supplied to the Java process.
  
  ## Running the Application
  
  ### GUI
  
  The graphical interface is the default mode.
  
  In Eclipse:
  
  1. Open `src/Main.java`.
  2. Right-click `Main.java`.
  3. Select **Run As → Java Application**.
  4. Leave Program Arguments empty.
  
  The Swing GUI will open with navigation for all 11 features.
  
  ### CLI
  
  To launch the command-line interface through Eclipse:
  
  1. Open **Run → Run Configurations**.
  2. Select the `Main` Java Application configuration.
  3. Open the **Arguments** tab.
  4. Enter:
  
  ```
  cli
  ```
  
  1. Apply and run.
  
  The CLI provides a numbered menu for the project features.
  
  ### Command-Line Compilation
  
  From the repository root in Windows PowerShell:
  
  ```
  New-Item -ItemType Directory -Force out | Out-Null
  javac --release 17 -d out (Get-ChildItem src -Recurse -Filter *.java).FullName
  ```
  
  Run the GUI:
  
  ```
  java -cp out Main
  ```
  
  Run the CLI:
  
  ```
  java -cp out Main cli
  ```
  
  ## Importing Ride Data
  
  The Ride Import feature accepts a Garmin activities-history CSV containing multiple activities.
  
  The importer extracts cycling activities and creates `Ride` objects using fields such as:
  
  - Date
  - Distance
  - Duration
  - Average speed
  - Average heart rate
  - Maximum heart rate
  - Average power
  - Maximum power
  - Average cadence
  
  Non-cycling activities are ignored.
  
  Duplicate rides are detected so importing the same Garmin CSV again does not create duplicate ride-history entries.
  
  A safe example CSV is available in:
  
  ```
  sample_data/
  ```
  
  ## Local Data Storage
  
  Runtime user data is stored locally under:
  
  ```
  data/
  ```
  
  The application persists:
  
  - Rider profile
  - Imported ride history
  - Fitness-progress history
  - Current generated training plan
  
  This allows the application state to survive after the program is closed and reopened.
  
  The `data/` directory is ignored by Git and should not be committed.
  
  ## AI Behavior
  
  The AI features use rider-profile information and recent ride summaries as context.
  
  Because the imported Garmin activity data contains summary metrics rather than full second-by-second ride data, the AI is instructed not to claim access to unsupported information such as:
  
  - interval structure
  - heart-rate drift
  - detailed pacing changes
  - time-in-zone distribution
  - normalized power
  
  AI responses distinguish recorded evidence from interpretation or suggestions when appropriate.
  
  ## Security
  
  Do not commit:
  
  - API keys
  - `.env`
  - runtime data
  - credentials
  
  The repository contains only `.env.example` as a safe configuration example.
  
  If an API key is accidentally committed publicly, revoke and replace it immediately.
  
  ## Notes and Limitations
  
  - Ride analysis currently uses summary ride metrics rather than FIT/time-series data.
  - The application stores one active weekly training plan rather than maintaining training-plan history.
  - Local file storage is used instead of a database.
  - DeepSeek availability and account balance are required for AI-powered features.
  - Detailed automated testing and behavioral testing are part of the later project testing stage.
