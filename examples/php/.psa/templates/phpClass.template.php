<?php

global $formFields;
global $namespace;
$data = json_encode($formFields);
$abstract = 'true' === $formFields['abstract'] ? 'abstract ' : '';
$returnType = $formFields['returnType'];
$className = $formFields['className'];

return <<<EOT
<?php

// $data

declare(strict_types=1);

namespace $namespace;

{$abstract}class $className {

    /**
     * @return $returnType
     */
    public function __construct() {

    }
}
EOT;
