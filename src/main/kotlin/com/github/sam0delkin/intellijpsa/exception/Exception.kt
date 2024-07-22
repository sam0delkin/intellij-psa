package com.github.sam0delkin.intellijpsa.exception

import java.lang.RuntimeException

class Exception {
    class IndexNotReadyException : RuntimeException()
    class IndexingDisabledException : RuntimeException()
}