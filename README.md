# Havenspuren: Wilhelmshaven City Tour App

## Overview

Havens Pure is a mobile application designed to enhance the tourist experience in Wilhelmshaven, offering an immersive and interactive way to explore the city's rich history and landmarks. Developed using Kotlin and Jetpack Compose, the app provides guided tours, navigation, and progress tracking for visitors.

## Features

- **Interactive City Tours**: Discover Wilhelmshaven's most interesting locations through curated tours
- **Detailed Navigation**: Powered by OpenStreetMap (OSRM) for precise route guidance
- **Progress Tracking**: Room database integration to save and resume user tour progress
- **Offline Accessibility**: Comprehensive tour information available without constant internet connection
- **User-Friendly Interface**: Intuitive navigation drawer and smooth screen transitions

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Navigation**: Jetpack Navigation Component
- **Database**: Room Persistence Library
- **Maps**: OpenStreetMap Routing Machine (OSRM)
- **Dependency Injection**: Custom dependency injection implementation

## Key Components

- **Location Services**: Comprehensive location permission handling
- **Tour Management**: 
  - Tour selection
  - Detailed tour information
  - Tour navigation
- **Offline Capabilities**: Local storage of tour data
- **Dynamic Routing**: Real-time route generation based on user location

## Prerequisites

- Android device running Android 8.0 (Oreo) or higher
- Location services enabled
- Minimum 50MB of free storage space

## Installation

1. Clone the repository
   ```bash
   git clone https://github.com/yourusername/havenspure-app.git
   ```

2. Open the project in Android Studio

3. Build and run the application on your Android device or emulator

## Getting Started

1. Launch the app
2. Grant location permissions
3. Browse available tours
4. Select a tour and start exploring Wilhelmshaven!

## Technical Architecture

- **MVVM Architecture**
- **Dependency Injection**: Custom DI graph implementation
- **Modular Design**: Separate modules for navigation, view models, and data management

## Permissions

The app requires the following permissions:
- Location Access
- Internet Access

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

Distributed under the MIT License. See `LICENSE` for more information.


## Acknowledgments

- OpenStreetMap
- Jetpack Compose 
- Kotlin Developer
