#!/usr/bin/env php
<?php

// Test fixture only - a one-shot "Script" mode script returning a minimal, strictly-shaped
// StaticCompletionsModel JSON payload (no extra keys), used to exercise the successful decode
// path of PsaManager.getStaticCompletions().

echo json_encode(['static_completions' => []]);
