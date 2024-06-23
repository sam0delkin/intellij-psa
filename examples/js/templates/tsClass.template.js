module.exports = {
  generateMyAwesomeTemplate: (formFields) => {
    const abstract = formFields['abstract'] ? '/* abstract */ ' : '';

    return `${abstract}class ${formFields['className']} {
    /**
    * @param ${formFields['comment']}
    */
    ${formFields['className']}() {
    }
}

`;
  }
}
