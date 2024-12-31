# litellmkts

## Description

**litellmkts** is a wrapper for the ollama REST API that enables its use in cross-platform projects. This project simplifies API integration and management, offering a consistent and developer-friendly interface.

## Features

- **Cross-platform:** Compatible with projects on [supported platforms, e.g., Android, iOS, Web].
- **Easy to use:** Designed to streamline communication with the REST API.
- **Extensible:** Modular structure allowing customizations as per project needs.

## Installation

### Using [Dependency Manager]

```gradle
implementation("io.github.lauro299:litellmkts:0.0.3")
```

### Manual

1. Download or clone the repository:
   ```bash
   git clone https://github.com/lauro299/litellmkts.git
   ```
2. Import the project into your development environment.

## Basic Usage

### Initial Setup

```kotlin

```

### Usage Example

```kotlin
//With koin
 single<ChatHandler> {
   val factory = get<HandlerFactory>()
   factory.createChatHandler("ollama")
}
.....
val chatHandler by inject<ChatHandler>()
chat.chat(
                        BaseParamsModel().also {
                            it["model"] = modelName
                            it["messages"] = _state.value.messages
                        }
                    )
                        .catch {
                            onEvent(Error)
                        }
                        .map { it.message?.content ?: "" }
                        .reduce { accumulator, value -> "$accumulator$value" })
```

## Documentation

For detailed information about available methods, visit the [full documentation](https://github.com/user/litellmkts/wiki).

## Contributions

Contributions are welcome. Please follow these steps:

1. Fork the repository.
2. Create a branch for your feature or bug fix:
   ```bash
   git checkout -b branch-name
   ```
3. Make your changes and push the branch:
   ```bash
   git push origin branch-name
   ```
4. Open a pull request.

## Handler Implementation

The `HandlerFactory` class is a key part of this project as it enables the creation of various types of handlers to manage different API functionalities. Below is an overview of its implementation:


## License

This project is licensed under MIT. See the `LICENSE` file for more details.

## Contact

For questions or suggestions, please contact:

- **Author:** lauro299
- **Email:** [youremail@example.com]
- **GitHub:** [https://github.com/lauro299](https://github.com/user)

