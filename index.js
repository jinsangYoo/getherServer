var http = require("http");
var express = require("express");
var bodyParser = require("body-parser");
var app = express();
var router = express.Router();
var fs = require("fs");

var format = require("date-format");

app.use(express.json());

app.use(
  bodyParser.urlencoded({
    extended: false
  })
);
app.use(bodyParser.json());

router.use(function(req, res, next) {
  // log each request to the console
  console.log(req.method, req.url);
  // continue doing what we were doing and go to the route
  next();
});

fs.mkdir("./logs", { recursive: true }, err => {
  if (err) throw err;
});

app.get("/", function(req, res) {
  console.log("GET call");
  console.log("***** req.headers: >>" + JSON.stringify(req.headers) + "<<");

  console.log("req.url: " + JSON.stringify(req.url));
  console.log("req.query: " + JSON.stringify(req.query));
  console.log("req.body: " + JSON.stringify(req.body));

  let fileName = format(new Date());
  fileName = `./logs/${fileName}.json`;
  console.log(`fileName: ${fileName}`);
  var out = fs.createWriteStream(fileName, {
    encoding: "utf8"
  });
  out.write(JSON.stringify(req.query));
  res.send(JSON.stringify(req.query));
});

http.createServer(app).listen(52273, function() {
  console.log("Express server listening on port(52273)");
});
