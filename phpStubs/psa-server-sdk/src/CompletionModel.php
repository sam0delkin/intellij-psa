<?php

namespace Psa;

final class CompletionModel
{
    /** @var string|null */
    public $text;

    /** @var bool */
    public $bold = false;

    /** @var string|null */
    public $presentableText;

    /** @var string|null */
    public $tailText;

    /** @var string|null */
    public $type;

    /** @var int|null */
    public $priority;

    /** @var string|null */
    public $link;

    public function toArray(): array
    {
        $data = ['bold' => $this->bold];

        if ($this->text !== null) {
            $data['text'] = $this->text;
        }
        if ($this->presentableText !== null) {
            $data['presentable_text'] = $this->presentableText;
        }
        if ($this->tailText !== null) {
            $data['tail_text'] = $this->tailText;
        }
        if ($this->type !== null) {
            $data['type'] = $this->type;
        }
        if ($this->priority !== null) {
            $data['priority'] = $this->priority;
        }
        if ($this->link !== null) {
            $data['link'] = $this->link;
        }

        return $data;
    }
}
