FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    HOME=/home/careerloop \
    XDG_CACHE_HOME=/home/careerloop/.cache

# MediaPipe ships its own bundled libgl/glib bindings, but the wheel still
# needs a few system libs at import time on slim images.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libgl1 \
        libglib2.0-0 \
        libsm6 \
        libxext6 \
        libxrender1 \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 careerloop \
    && useradd --system --uid 10001 --gid careerloop --home-dir /home/careerloop --create-home careerloop \
    && install -d -o careerloop -g careerloop -m 700 /home/careerloop/.cache

WORKDIR /app
COPY requirements.txt /app/requirements.txt
RUN pip install -r requirements.txt

COPY --chown=careerloop:careerloop main.py /app/main.py
RUN chmod 755 /app && chmod 644 /app/main.py

USER careerloop

EXPOSE 8001
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/health', timeout=3).read()" || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]
