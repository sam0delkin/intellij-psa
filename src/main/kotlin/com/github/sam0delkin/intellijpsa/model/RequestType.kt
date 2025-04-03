package com.github.sam0delkin.intellijpsa.model

enum class RequestType {
    Completion,
    BatchCompletion,
    GoTo,
    BatchGoTo,
    Info,
    GenerateFileFromTemplate,
    GetStaticCompletions,
    PerformEditorAction,
}
