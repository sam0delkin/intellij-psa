<?php

namespace Psa;

final class RequestContext
{
    /** @var string|null */
    public $id;

    /** @var string|null */
    public $type;

    /** @var string|null */
    public $language;

    /** @var bool */
    public $debug = false;

    /**
     * null means "no offset". Unlike the one-shot Script mode's PSA_OFFSET env var (which uses ""
     * for the same case, since env vars are string-only), this is a true nullable int.
     *
     * @var int|null
     */
    public $offset;

    /** @var array|null */
    public $context;

    public static function fromArray(array $data): self
    {
        $instance = new self();
        $instance->id = $data['id'] ?? null;
        $instance->type = $data['type'] ?? null;
        $instance->language = $data['language'] ?? null;
        $instance->debug = $data['debug'] ?? false;
        $instance->offset = $data['offset'] ?? null;
        $instance->context = $data['context'] ?? null;

        return $instance;
    }
}
