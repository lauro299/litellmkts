# litellmkts

## Description

**litellmkts** is a wrapper for the [API_NAME] REST API that enables its use in cross-platform projects. This project simplifies API integration and management, offering a consistent and developer-friendly interface.

## Features

- **Cross-platform:** Compatible with projects on [supported platforms, e.g., Android, iOS, Web].
- **Easy to use:** Designed to streamline communication with the REST API.
- **Extensible:** Modular structure allowing customizations as per project needs.
- **Authentication support:** Implementation of [authentication types, e.g., OAuth2, API keys].

## Installation

### Using [Dependency Manager]

```bash
[command to install the library]
```

### Manual

1. Download or clone the repository:
   ```bash
   git clone https://github.com/[user]/litellmkts.git
   ```
2. Import the project into your development environment.

## Basic Usage

### Initial Setup

```kotlin
val apiClient = LitellmktsClient(
    baseUrl = "https://api.example.com",
    apiKey = "your-api-key"
)
```

### Usage Example

```kotlin
val products = apiClient.getProducts()
products.forEach { product ->
    println(product.name)
}
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

### HandlerFactory

```kotlin
package org.litellmkt.handlers

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton
import org.litellmkt.handlers.ollama.OllamaChatHandler
import org.litellmkt.handlers.ollama.OllamaEmbeddingHandler
import org.litellmkt.handlers.ollama.OllamaGenerateGenerationHandler

/**
 * The HandlerFactory class is a factory that creates instances of different types of handlers based on the provided
 * instance string. It has three methods for creating chat, embedding, and generation handlers. The factory uses
 * dependency injection to provide the required dependencies to the handler constructors.
 */
@Singleton
class HandlerFactory(
    private val parser: Json,
    @Named("baseUrl") private val baseUrl: String,
    private val httpClient: HttpClient
) {
    fun createChatHandler(instance: String): ChatHandler {
        return when (instance) {
            "ollama" -> OllamaChatHandler(
                parser = parser,
                baseUrl = baseUrl,
                httpClient = httpClient
            )

            else -> throw IllegalArgumentException("Invalid name: $instance")
        }
    }

    fun createEmbeddingHandler(instance: String): EmbeddingHandler {
        return when (instance) {
            "ollama" -> OllamaEmbeddingHandler(
                baseUrl = baseUrl,
                httpClient = httpClient
            )

            else -> throw IllegalArgumentException("Invalid name: $instance")
        }
    }

    fun createGenerationHandler(instance: String): GenerationHandler {
        return when (instance) {
            "ollama" -> OllamaGenerateGenerationHandler(
                parser = parser,
                baseUrl = baseUrl,
                httpClient = httpClient
            )

            else -> throw IllegalArgumentException("Invalid name: $instance")
        }
    }
}
```

## License

This project is licensed under [license name]. See the `LICENSE` file for more details.

## Contact

For questions or suggestions, please contact:

- **Author:** [Your name or nickname]
- **Email:** [youremail@example.com]
- **GitHub:** [https://github.com/user](https://github.com/user)

