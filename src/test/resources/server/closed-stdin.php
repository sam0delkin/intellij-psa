#!/usr/bin/env php
<?php

// Test fixture only - answers the health-check "Info" request successfully (so the manager
// reaches RUNNING) and then closes its own STDIN read end while staying alive, so any further
// write to this process's stdin pipe from the parent fails - used to exercise
// ServerManager.sendRequest()'s stdin-write-failure catch deterministically (as opposed to
// racing an actual process crash/termination).

$line = fgets(STDIN);
$request = json_decode(trim((string) $line), true);
$id = $request['id'] ?? null;
echo json_encode(['id' => $id, 'result' => []]) . "\n";
flush();

fclose(STDIN);

while (true) {
    usleep(100000);
}
