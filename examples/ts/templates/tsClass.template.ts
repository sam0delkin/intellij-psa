export const generateMyAwesomeTemplate = (formFields: Record<string, string>): string => {
    const abstract = formFields['abstract'] ? 'abstract ' : '';

    return `${abstract}class ${formFields['className']} {
    // ${JSON.stringify(formFields)}

    /**
    * @param ${formFields['comment']}
    */
    constructor() {
    }
}

`;
}
