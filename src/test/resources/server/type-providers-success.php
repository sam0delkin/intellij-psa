#!/usr/bin/env php
<?php

// Test fixture only - a one-shot "Script" mode script returning a minimal, strictly-shaped
// TypeProvidersModel JSON payload (no extra keys), used to exercise the successful decode path
// of PhpPsaManager.getTypeProviders().

echo json_encode(['providers' => []]);
