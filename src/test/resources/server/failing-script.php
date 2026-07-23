#!/usr/bin/env php
<?php

// Test fixture only - a one-shot "Script" mode script that always fails, used to exercise the
// non-zero exit code error path of PhpPsaManager.getTypeProviders().

fwrite(STDERR, "boom");
exit(1);
