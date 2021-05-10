var http = require("http");
var express = require("express");
var bodyParser = require("body-parser");
var app = express();
var router = express.Router();
var fs = require("fs");
const cors = require("cors");
app.use(cors());

var format = require("date-format");
const jwt = require("jsonwebtoken");

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

  res.writeHead(200, {
    "Content-Type": "text/html; charset=utf-8",
    "Accept-CH": "UA, Platform, Model, Mobile, Arch"
  });
  fs.createReadStream("index.html", {
    encoding: "utf8"
  }).pipe(res);
});

app.get("/index.html", function(req, res) {
  console.log("GET call");
  console.log("***** req.headers: >>" + JSON.stringify(req.headers) + "<<");

  console.log("req.url: " + JSON.stringify(req.url));
  console.log("req.query: " + JSON.stringify(req.query));
  console.log("req.body: " + JSON.stringify(req.body));

  fs.readFile("./index.html", function(error, data) {
    res.writeHead(200, {
      "Content-Type": "text/html; charset=utf-8",
      "Accept-CH": "UA, Platform"
    });
    res.end(data);
  });
});

app.get("/index2.html", function(req, res) {
  console.log("GET call");
  console.log("***** req.headers: >>" + JSON.stringify(req.headers) + "<<");

  console.log("req.url: " + JSON.stringify(req.url));
  console.log("req.query: " + JSON.stringify(req.query));
  console.log("req.body: " + JSON.stringify(req.body));

  res.writeHead(200, {
    "Content-Type": "text/html; charset=utf-8",
    "Accept-CH": "UA, Platform, Arch"
  });
  fs.createReadStream("index2.html", {
    encoding: "utf8"
  }).pipe(res);
});

app.get("/json", function(req, res) {
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
  res.setHeader("Accept-CH", "UA, UA-Platform, UA-Arch");
  res.send(JSON.stringify(req.query));
});

app.get("/policy", function(req, res) {
  console.log("GET call");
  console.log("***** req.headers: >>" + JSON.stringify(req.headers) + "<<");

  console.log("req.url: " + JSON.stringify(req.url));
  console.log("req.query: " + JSON.stringify(req.query));
  console.log("req.body: " + JSON.stringify(req.body));

  res.status(200);
  res.setHeader("Content-Type", "application/json");
  res.setHeader("Cp-Allow", "*");
  res.setHeader("Cp-App", "0");
  res.setHeader("Cp-Cid", "dummyCid");
  res.setHeader("Cp-Debug", "0");
  res.setHeader("Cp-Domain", "http://192.168.0.4:52274/");
  res.setHeader("Cp-Private", "0");
  res.setHeader("Cp-Source-Ip", "127.0.0.1");
  res.setHeader("Cp-Force-Stop", "0");
  res.setHeader("Cp-Force-Delete-FailedLogs", "0");
  res.setHeader("Cp-Crash-Domain", "http://192.168.0.4:52274/");
  res.setHeader("Cp-Repeat-Interval", "21600");
  res.setHeader("Cp-LNC-Id", "1iAMEe1l2dAylAF1");

  res.send("done");
});

app.post("/debuglog", function(req, res) {
  console.log("POST call");
  console.log("***** req.headers: >>" + JSON.stringify(req.headers) + "<<");

  console.log("req.url: " + JSON.stringify(req.url));
  console.log("req.query: " + JSON.stringify(req.query));
  console.log("req.body: " + JSON.stringify(req.body));

  res.status(200);
  res.setHeader("Content-Type", "application/json");
  res.send("done");
});

app.get("/sign", function(req, res) {
  const ALGORITHM = "ES256";
  const APNS_KEY_ID = "55M8FH3JR2";
  const APNS_AUTH_KEY = "./AuthKey_55M8FH3JR2.p8";
  const TEAM_ID = "HA4JNBHU77";
  const BUNDLE_ID = "com.acecounter.aceappplus";
  const DEVICE_TOKEN =
    "27163d3cf126382289ab873b0eb02c420efe0707c027ea1fe4f91458f2d07204";

  console.log("sign call");
  console.log("***** req.headers: >>" + JSON.stringify(req.headers) + "<<");

  console.log("req.url: " + JSON.stringify(req.url));
  console.log("req.query: " + JSON.stringify(req.query));
  console.log("req.body: " + JSON.stringify(req.body));
  console.log(
    "(Math.floor(Date.now() / 1000)): " + Math.floor(Date.now() / 1000)
  );

  fs.readFile(APNS_AUTH_KEY, function(error, secret) {
    res.setHeader("Content-Type", "application/json");
    if (error) {
      res.status(503);
      res.send("failed readFile::" + error);
    }

    jwt.sign(
      {
        iss: TEAM_ID,
        iat: Math.floor(Date.now() / 1000)
      },
      secret,
      {
        header: {
          alg: ALGORITHM,
          kid: APNS_KEY_ID
        }
      },
      function(err, encoded) {
        if (err) {
          res.status(503);
          res.send("failed sign::" + err);
        } else {
          res.status(200);
          res.send(encoded);
        }
      }
    );
  });
});

let port = 52274;
http.createServer(app).listen(port, function() {
  console.log(`Express server listening on port(${port})`);
});
