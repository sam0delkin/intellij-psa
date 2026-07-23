<?php

namespace Psa;

final class Server
{
    /** @var array<string, callable> */
    private $handlers = [];

    /** @var callable|null */
    private $afterResponse;

    public function on(string $type, callable $handler): self
    {
        $this->handlers[$type] = $handler;

        return $this;
    }

    /**
     * Fires after every response (not just the first), e.g. to clear/warm caches for the next
     * request. Runs synchronously right after the current response is flushed, so it never
     * delays that response - but it blocks the next request if slow (no threading in PHP); fork
     * (pcntl_fork) for real background work.
     */
    public function afterResponse(callable $handler): self
    {
        $this->afterResponse = $handler;

        return $this;
    }

    public function onInfo(callable $handler): self
    {
        return $this->on('Info', $handler);
    }

    public function onCompletion(callable $handler): self
    {
        return $this->on('Completion', $handler);
    }

    public function onGoTo(callable $handler): self
    {
        return $this->on('GoTo', $handler);
    }

    /** Writes to STDERR - never echo()/print(), it would corrupt the NDJSON stdout stream. */
    public function log(string $message): void
    {
        fwrite(STDERR, $message . "\n");
    }

    public function run(): void
    {
        while (($line = fgets(STDIN)) !== false) {
            $line = trim($line);

            if ($line === '') {
                continue;
            }

            $request = json_decode($line, true);
            $id = is_array($request) ? ($request['id'] ?? null) : null;
            $context = null;

            try {
                if (!is_array($request) || !isset($request['type'])) {
                    throw new \RuntimeException('Malformed request');
                }

                $context = RequestContext::fromArray($request);
                $handler = $this->handlers[$request['type']] ?? null;
                $result = null;

                if ($handler !== null) {
                    $result = $handler($context);

                    if ($result instanceof InfoResponse || $result instanceof CompletionsResponse) {
                        $result = $result->toArray();
                    }
                }

                echo json_encode(['id' => $id, 'result' => $result]) . "\n";
            } catch (\Throwable $e) {
                echo json_encode(['id' => $id, 'error' => $e->getMessage()]) . "\n";
            }

            flush();

            if ($this->afterResponse !== null && $context !== null) {
                ($this->afterResponse)($context);
            }
        }
    }
}
