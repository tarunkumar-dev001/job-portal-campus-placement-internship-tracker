import { cloneElement } from 'react';

function fieldLabel(control) {
  return control.labels?.[0]?.textContent?.replace(/\s*\((required|optional)\)\s*/i, '').trim()
    || control.getAttribute('aria-label')
    || 'This field';
}

function validationMessage(control) {
  const label = fieldLabel(control);
  const { validity } = control;
  if (validity.valueMissing) return `${label} is required.`;
  if (validity.typeMismatch) return `Enter a valid ${label.toLowerCase()}.`;
  if (validity.tooShort) return `${label} must contain at least ${control.minLength} characters.`;
  if (validity.tooLong) return `${label} must contain no more than ${control.maxLength} characters.`;
  if (validity.rangeUnderflow) return `${label} must be at least ${control.min}.`;
  if (validity.rangeOverflow) return `${label} must be no more than ${control.max}.`;
  if (validity.stepMismatch || validity.badInput) return `Enter a valid value for ${label.toLowerCase()}.`;
  return control.validationMessage || `${label} is invalid.`;
}

export function validateForm(form, setErrors) {
  const controls = Array.from(form.elements).filter(control => control.willValidate && control.name);
  const errors = {};
  let firstInvalid;

  controls.forEach(control => {
    if (!control.checkValidity()) {
      errors[control.name] = validationMessage(control);
      firstInvalid ||= control;
    }
  });

  setErrors(Object.keys(errors).length
    ? { ...errors, _form: 'Please correct the highlighted fields and try again.' }
    : {});
  if (firstInvalid) {
    window.requestAnimationFrame(() => firstInvalid.focus());
    return false;
  }
  return true;
}

export function clearFieldError(setErrors, name) {
  if (!name) return;
  setErrors(current => {
    if (!(name in current) && !current._form) return current;
    const next = { ...current };
    delete next[name];
    delete next._form;
    return next;
  });
}

export function getServerFieldErrors(error) {
  const source = error?.response?.data?.fieldErrors
    || error?.response?.data?.errors
    || error?.response?.data?.violations;
  if (!source || (!Array.isArray(source) && typeof source !== 'object')) return {};
  const entries = Array.isArray(source)
    ? source.map(item => [item?.field || item?.propertyPath, item?.message || item?.defaultMessage])
    : Object.entries(source);
  return entries.reduce((result, [field, value]) => {
    const name = typeof field === 'string' ? field.split('.').pop() : '';
    const message = typeof value === 'string'
      ? value : value?.message || value?.defaultMessage;
    if (name && typeof message === 'string' && message.trim()) result[name] = message;
    return result;
  }, {});
}

export function FormErrorSummary({ errors, className = '' }) {
  if (!errors?._form) return null;
  return <p role="alert" aria-live="assertive" className={`rounded bg-red-50 p-3 text-sm text-red-800 ${className}`}>
    {errors._form}
  </p>;
}

export default function FormField({
  id, label, required = false, error, className = '', children,
}) {
  const childProps = children.props;
  const errorId = `${id}-error`;
  const describedBy = [childProps['aria-describedby'], error ? errorId : null]
    .filter(Boolean).join(' ') || undefined;
  const control = cloneElement(children, {
    id,
    required,
    'aria-invalid': error ? 'true' : undefined,
    'aria-describedby': describedBy,
    className: `${childProps.className || ''} ${error ? 'border-red-500' : ''} focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:ring-offset-2`,
  });

  return <div className={className}>
    <label htmlFor={id} className="mb-1 block text-sm font-medium text-slate-800">
      {label} <span className="text-xs font-normal text-slate-500">({required ? 'required' : 'optional'})</span>
    </label>
    {control}
    {error && <p id={errorId} className="mt-1 text-sm text-red-700">{error}</p>}
  </div>;
}
