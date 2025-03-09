package com.github.sam0delkin.intellijpsa.util

class PropertyAccessor {
    companion object {
        fun getPropertyValue(
            obj: Any,
            propertyPath: String,
        ): String? {
            val parts = propertyPath.split(".")
            var current: Any? = obj

            for (part in parts) {
                if (current == null) return null

                current =
                    when {
                        part.matches(Regex("\\d+")) -> {
                            // Handle list/array index access (e.g., "0")
                            getIndexedProperty(current, part.toInt())
                        }

                        current is Map<*, *> -> {
                            // Handle map key access (e.g., "key")
                            getMapProperty(current, part)
                        }

                        else -> {
                            // Handle regular property access (e.g., "options" or "model")
                            getSimpleProperty(current, part)
                        }
                    }
            }

            return current?.toString()
        }

        private fun getSimpleProperty(
            obj: Any,
            propertyName: String,
        ): Any? =
            obj::class
                .members
                .find { it.name == propertyName }
                ?.call(obj)

        private fun getIndexedProperty(
            obj: Any,
            index: Int,
        ): Any? =
            when (obj) {
                is List<*> -> obj.getOrNull(index)
                is Array<*> -> obj.getOrNull(index)
                else -> throw IllegalArgumentException("Object is not a List or Array: $obj")
            }

        private fun getMapProperty(
            map: Any,
            key: String,
        ): Any? =
            when (map) {
                is Map<*, *> -> map[key]
                else -> throw IllegalArgumentException("Object is not a Map: $map")
            }

        fun convertToPropertyPathMap(
            obj: Any,
            initialPath: String = "",
        ): Map<String, String> {
            val result = mutableMapOf<String, String>()
            traverseObject(obj, initialPath, result)

            return result
        }

        private fun traverseObject(
            obj: Any?,
            currentPath: String,
            result: MutableMap<String, String>,
        ) {
            if (obj == null) return

            when (obj) {
                is Map<*, *> -> {
                    // Handle Map entries
                    for ((key, value) in obj) {
                        val newPath = if (currentPath.isEmpty()) key.toString() else "$currentPath.$key"
                        traverseObject(value, newPath, result)
                    }
                }

                is List<*> -> {
                    // Handle List elements
                    for ((index, value) in obj.withIndex()) {
                        val newPath = if (currentPath.isEmpty()) "$index" else "$currentPath.$index"
                        traverseObject(value, newPath, result)
                    }
                }

                is Array<*> -> {
                    // Handle Array elements
                    for ((index, value) in obj.withIndex()) {
                        val newPath = if (currentPath.isEmpty()) "$index" else "$currentPath.$index"
                        traverseObject(value, newPath, result)
                    }
                }

                else -> {
                    // Handle regular objects
                    if (obj::class.isData) {
                        // Use reflection to get properties of data classes
                        obj::class.members.forEach { member ->
                            if (member is kotlin.reflect.KProperty<*>) {
                                val value = member.call(obj)
                                val newPath = if (currentPath.isEmpty()) member.name else "$currentPath.${member.name}"
                                traverseObject(value, newPath, result)
                            }
                        }
                    } else {
                        // For non-data classes, treat the object as a single value
                        result[currentPath] = obj.toString()
                    }
                }
            }
        }
    }
}
