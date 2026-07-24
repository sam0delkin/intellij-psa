<?php

namespace Psa;

final class CompletionsResponse
{
    /** @var CompletionModel[] */
    public $completions = [];

    /** @var array[] */
    public $notifications = [];

    public function addCompletion(CompletionModel $completion): self
    {
        $this->completions[] = $completion;

        return $this;
    }

    public function addNotification(string $type, string $text): self
    {
        $this->notifications[] = ['type' => $type, 'text' => $text];

        return $this;
    }

    public function toArray(): array
    {
        return [
            'completions' => array_map(static function (CompletionModel $completion): array {
                return $completion->toArray();
            }, $this->completions),
            'notifications' => $this->notifications,
        ];
    }
}
