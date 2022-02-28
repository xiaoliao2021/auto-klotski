from flask import Flask, send_from_directory
from gevent import pywsgi

app = Flask(__name__, static_folder='dist', static_url_path='/')


@app.route("/")
def index():
    return send_from_directory("dist", "index.html")


def run_server():
    # app.run(host="0.0.0.0", port=8888, debug=False)
    server = pywsgi.WSGIServer(('0.0.0.0', 8888), app)
    server.serve_forever()