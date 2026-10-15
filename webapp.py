from http.server import BaseHTTPRequestHandler, HTTPServer
import socket

class WebHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path == "/":
            hostname = socket.gethostname()

            message = f"""
            Hello from Kubernetes Rolling Update!

            Application: Jenkins Python Demo
            Pod Hostname: {hostname}
            Version: 2.0
            """

            self.send_response(200)
            self.send_header("Content-type", "text/plain")
            self.end_headers()
            self.wfile.write(message.encode())

        elif self.path == "/health":
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"OK")

        else:
            self.send_response(404)
            self.end_headers()

if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", 8081), WebHandler)

    print("Python web server running on port 8081", flush=True)

    server.serve_forever()
