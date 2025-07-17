module.exports = {
  generateMyAwesomeTemplate: (formFields) => {
    const abstract = formFields['abstract'] ? '/* abstract */ ' : '';

    return `${abstract}class ${formFields['className']} {
    // ${JSON.stringify(formFields)}

    /**
    * @param ${formFields['comment']}
    */
    ${formFields['className']}() {
    }
}

`;
  }
}
