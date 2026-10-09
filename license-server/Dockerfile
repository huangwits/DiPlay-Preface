FROM python:3.13-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && useradd --uid 10001 --create-home diplay \
    && mkdir /data && chown diplay:diplay /data
COPY app.py ./
COPY static ./static
USER 10001:10001
ENV DIPLAY_LICENSE_DATA=/data
EXPOSE 8787
CMD ["gunicorn", "--bind", "0.0.0.0:8787", "--workers", "2", "--threads", "2", "--timeout", "30", "app:application"]
