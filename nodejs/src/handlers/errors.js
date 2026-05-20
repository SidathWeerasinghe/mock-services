'use strict';

function writeValidationError(res, err) {
  res.status(400).json({
    status: 400,
    error: 'Bad Request',
    message: err.message,
    timestamp: new Date().toISOString(),
    hint: 'Valid sizes (KB): 1,2,3,4,5,6,7,8,9,10,15,20 | formats: json, xml',
  });
}

function handlePayloadError(res, err) {
  if (err && err.message && err.message.includes('Invalid size')) {
    writeValidationError(res, err);
    return true;
  }
  return false;
}

module.exports = { writeValidationError, handlePayloadError };
