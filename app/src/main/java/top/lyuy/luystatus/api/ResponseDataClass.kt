package top.lyuy.luystatus.api

import com.google.gson.JsonObject

// error

data class ErrorResponse(
    val error: String
)

//  push

data class PushResponse(
    val success: Boolean,
    val position: Int
)

//  peek / pop

data class PeekResponse(
    val success: Boolean,
    val data: JsonObject
)

data class PopResponse(
    val success: Boolean,
    val data: JsonObject
)

// size

data class SizeResponse(
    val success: Boolean,
    val size: Int
)

// list

data class ListResponse(
    val success: Boolean,
    val items: List<ListItem>
)

data class ListItem(
    val index: Int,
    val data: JsonObject
)
