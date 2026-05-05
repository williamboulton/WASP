# Use the official Nginx image as the base
FROM nginx:latest

# Copy your local static files to the Nginx default directory
COPY Native/src/ /usr/share/nginx/html/

# Expose port 80 to allow traffic
EXPOSE 80
