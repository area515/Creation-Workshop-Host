var printerName = null;

function getPrinterNameIfNotAvailable() {
    $.getJSON("/services/printers/getFirstAvailablePrinter").done(function (data){
    	setTimeout(getPrinterNameIfNotAvailable, 15000);
    	printerName = data.configuration.name;
    })
    .error(function (data){
    	$.getJSON("/services/printers/list").done(function (data){
        	setTimeout(getPrinterNameIfNotAvailable, 1000);
        	printerName = data[0].configuration.name;
        })
        .error(function (data) {
        	setTimeout(getPrinterNameIfNotAvailable, 500);
        })
    });
}

getPrinterNameIfNotAvailable();