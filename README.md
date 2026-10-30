# AI Cycling Training Coach

EECS 3311 Fall 2026 Course Project

An AI-powered cycling training coach that helps recreational and amateur cyclists organize their training, analyze cycling data, track progress, and receive personalized workout recommendations.

The application is designed to combine deterministic software components with an AI/LLM-based training assistant. It will support features such as rider profile management, ride-data import, ride analysis, weekly training-plan generation, progress tracking, ride comparison, workout planning, personal-best detection, and AI-assisted training guidance.

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
In progress.

Current development includes:
- Java project setup
- Git/GitHub integration
- Source-code and test structure
- Local configuration and API-key protection
- Implementation of the Stage 1 design

## Planned Technologies

- Java
- Eclipse
- Git and GitHub
- DeepSeek API
- CSV ride-data import

## Main Design Patterns

The project is designed to use:
- Adapter
- Facade
- Observer
- Strategy
- MVC

## Repository Structure

```text
Stage1/       Stage 1 design documents and UML diagrams
src/          Application source code
test/         Automated tests
sample_data/  Sample ride data used for development and demonstrations
docs/         Project documentation and later-stage reports
```

## Configuration

API keys and other credentials must not be committed to the repository.

A safe example configuration is provided in:

```text
.env.example
```

A local `.env` file will be used for real credentials and is excluded from Git through `.gitignore`.
