;(function (window) {
  if (window.isConsoleHooked) {
    console.log('Console hook has already been applied.');
    return;
  }

  function printObject(obj) {
    if (obj === null) return 'null';
    if (typeof obj === 'undefined') return 'undefined';
    if (obj instanceof Promise) return 'This is a javascript Promise.';
    if (obj instanceof Date) return obj.getTime().toString();
    if (Array.isArray(obj)) return `[${obj.toString()}]`;
    if (typeof obj === 'object') {
      const entries = Object.entries(obj).map(([key, value]) => `"${key}":"${value}"`);
      return `{${entries.join(',')}}`;
    }
    return String(obj);
  }

  console.log('Starting console hook application.');

  const originalConsoleLog = window.console.log;

  window.console.log = function (...args) {
    window.isConsoleHooked = true;
    args.forEach(obj => {
      originalConsoleLog.call(window.console, obj);
      const message = printObject(obj);
      window.consolePipe.receiveConsole(message);
    });
  };

  console.log('Console hook has been applied successfully.');
})(window);
