<?php

namespace Psa;

final class InfoResponse
{
    /** @var string[] */
    public $supportedLanguages = [];

    /** @var string[]|null */
    public $goToElementFilter;

    /** @var bool */
    public $supportsBatch = false;

    /** @var bool */
    public $supportsStaticCompletions = false;

    public function toArray(): array
    {
        $data = [
            'supported_languages' => $this->supportedLanguages,
            'supports_batch' => $this->supportsBatch,
            'supports_static_completions' => $this->supportsStaticCompletions,
        ];

        if ($this->goToElementFilter !== null) {
            $data['goto_element_filter'] = $this->goToElementFilter;
        }

        return $data;
    }
}
