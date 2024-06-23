export const generateMyAwesomeTemplate = (formFields: Record<string, string>): string => {
    const abstract = formFields['abstract'] ? 'abstract ' : '';

    return `${abstract}class ${formFields['className']} {
    /**
    * @param ${formFields['comment']}
    */
    constructor() {
    }
}

`;
}
