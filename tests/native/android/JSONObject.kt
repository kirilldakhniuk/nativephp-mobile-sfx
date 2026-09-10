package org.json
class JSONObject(private val values: Map<String, Any?>) {
    fun keys() = values.keys.iterator()
    fun opt(key: String) = values[key]
}
