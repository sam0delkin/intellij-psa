#!/usr/bin/env php
<?php

// Test fixture only - exits immediately on launch, without reading any input, so
// ServerManagerTest can exercise the auto-restart-with-backoff loop end-to-end
// (each respawn crashes again right away) without needing to send explicit "Crash" requests.
exit(1);
