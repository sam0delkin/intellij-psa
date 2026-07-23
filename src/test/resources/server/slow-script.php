#!/usr/bin/env php
<?php

// Test fixture only - a one-shot "Script" mode script that sleeps briefly before responding,
// used to exercise the cancellation path of PhpPsaManager.getTypeProviders() while the process
// is still running.

usleep(500000);
echo json_encode(['providers' => []]);
