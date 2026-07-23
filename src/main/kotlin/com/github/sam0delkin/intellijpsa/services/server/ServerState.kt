package com.github.sam0delkin.intellijpsa.services.server

enum class ServerState {
    STOPPED,
    STARTING,
    RUNNING,
    RETRYING,
    RESTARTING,
    FAILED,
}
