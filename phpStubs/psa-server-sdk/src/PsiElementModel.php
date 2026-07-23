<?php

namespace Psa;

final class PsiElementModel
{
    /** @var string|null */
    public $id;

    /** @var string|null */
    public $elementType;

    /** @var array */
    public $options = [];

    /** @var string|null */
    public $elementName;

    /** @var string|null */
    public $elementFqn;

    /** @var string[]|null */
    public $elementSignature;

    /** @var string|null */
    public $text;

    /** @var PsiElementModel|null */
    public $parent;

    /** @var PsiElementModel|null */
    public $prev;

    /** @var PsiElementModel|null */
    public $next;

    /** @var array|null */
    public $textRange;

    /** @var string|null */
    public $filePath;

    public static function fromArray(?array $data): ?self
    {
        if ($data === null) {
            return null;
        }

        $model = new self();
        $model->id = $data['id'] ?? null;
        $model->elementType = $data['elementType'] ?? null;
        $model->options = $data['options'] ?? [];
        $model->elementName = $data['elementName'] ?? null;
        $model->elementFqn = $data['elementFqn'] ?? null;
        $model->elementSignature = $data['elementSignature'] ?? null;
        $model->text = $data['text'] ?? null;
        $model->parent = self::fromArray($data['parent'] ?? null);
        $model->prev = self::fromArray($data['prev'] ?? null);
        $model->next = self::fromArray($data['next'] ?? null);
        $model->textRange = $data['textRange'] ?? null;
        $model->filePath = $data['filePath'] ?? null;

        return $model;
    }
}
