"""Render README images with Pillow. All displayed payment data is synthetic.

Run: python -m pip install -r tools/requirements-images.txt
     python tools/render_readme_images.py
"""
from pathlib import Path
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs' / 'images'
INK = '#19372e'
MUTED = '#60766c'
GREEN = '#2d7458'
WHITE = '#ffffff'


def font(size, bold=False):
    choices = [
        f'C:/Windows/Fonts/{"segoeuib" if bold else "segoeui"}.ttf',
        f'/usr/share/fonts/truetype/dejavu/DejaVuSans{"-Bold" if bold else ""}.ttf',
        'DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf',
    ]
    for path in choices:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default(size=size)


def canvas(width, height):
    image = Image.new('RGB', (width, height))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            mint = max(0, 1-math.hypot(x-width*.12, y-height*.25)/(width*.8))
            lilac = max(0, 1-math.hypot(x-width*.92, y-height*.8)/(width*.7))
            pixels[x, y] = (int(247-20*mint-5*lilac), int(249-5*mint-13*lilac), int(247-12*mint+2*lilac))
    return image


def paragraph(draw, content, x, y, max_width, size=25, fill=MUTED, bold=False, spacing=10):
    face = font(size, bold)
    for paragraph in content.split('\n'):
        line = ''
        for word in paragraph.split():
            trial = f'{line} {word}'.strip()
            if draw.textlength(trial, font=face) > max_width and line:
                draw.text((x, y), line, font=face, fill=fill)
                y += size + spacing
                line = word
            else:
                line = trial
        if line:
            draw.text((x, y), line, font=face, fill=fill)
            y += size + spacing
    return y


def title(image, eyebrow, heading, description):
    d = ImageDraw.Draw(image)
    d.text((75, 45), eyebrow.upper(), font=font(20, True), fill=GREEN)
    d.text((75, 85), heading, font=font(53, True), fill=INK)
    d.text((77, 157), description, font=font(25), fill=MUTED)


def card(image, box, label, heading, content, tint='#edf5ef', accent=GREEN):
    x, y, right, bottom = box
    shadow = Image.new('RGBA', image.size)
    s = ImageDraw.Draw(shadow)
    s.rounded_rectangle((x, y+10, right, bottom+10), radius=25, fill=(28, 65, 46, 22))
    image.paste(shadow.filter(ImageFilter.GaussianBlur(13)), (0, 0), shadow.filter(ImageFilter.GaussianBlur(13)))
    d = ImageDraw.Draw(image)
    d.rounded_rectangle(box, radius=25, fill=WHITE, outline='#dbe7df', width=2)
    d.rounded_rectangle((x+22, y+22, x+155, y+56), radius=12, fill=tint)
    d.text((x+34, y+26), label, font=font(16, True), fill=accent)
    yy = paragraph(d, heading, x+24, y+72, right-x-48, 29, INK, True, 6)
    paragraph(d, content, x+24, yy+10, right-x-48, 23, MUTED, False, 8)


def arrow(image, points, fill='#7a9e88', width=4, head=13, both=False):
    d = ImageDraw.Draw(image)
    d.line(points, fill=fill, width=width, joint='curve')
    def tip(a, b):
        angle = math.atan2(b[1]-a[1], b[0]-a[0])
        vertices = [b, (b[0]-head*math.cos(angle-.55), b[1]-head*math.sin(angle-.55)), (b[0]-head*math.cos(angle+.55), b[1]-head*math.sin(angle+.55))]
        d.polygon(vertices, fill=fill)
    tip(points[-2], points[-1])
    if both:
        tip(points[1], points[0])


def architecture():
    im = canvas(1800, 1100)
    title(im, 'UPI Ledger / architecture', 'From a shared image to a private ledger', 'Java handles Android and storage. The glass interface and analysis run locally.')
    nodes = [
        ((75, 240, 515, 425), 'INPUT', 'Receipt image', 'BHIM / Google Pay Share\nor the Android image picker'),
        ((680, 240, 1120, 425), 'ANDROID', 'MainActivity', 'Receives a temporary image URI\nCoordinates the local workflow'),
        ((1285, 240, 1725, 425), 'ON-DEVICE', 'ML Kit OCR', 'Bundled Latin-script model\nRecognizes text and positions'),
        ((1285, 520, 1725, 705), 'EXTRACTION', 'ReceiptText + Parser', 'Reading order and currency checks\nAmount, vendor, date and reference'),
        ((680, 520, 1120, 705), 'LOCAL UI', 'Review and edit', 'Correct fields, choose a category\nAdd remarks before saving'),
        ((75, 520, 515, 705), 'PRIVATE DATA', 'SQLite / LedgerDb', 'Categories, vendors, aliases\nPayments and monthly schedules'),
        ((75, 805, 515, 990), 'USER ACTION', 'JSON backup / restore', 'Export or replace saved records\nTransactional validation'),
        ((680, 805, 1725, 990), 'LOCAL ANALYSIS', 'model.js + the glass interface', 'Weekly and monthly totals, category shares, daily trends and vendor histories\nTotals use integer paise; vendor defaults stay separate from payment categories'),
    ]
    for b, tag, heading, body in nodes:
        card(im, b, tag, heading, body)
    arrow(im, [(515, 330), (680, 330)])
    arrow(im, [(1120, 330), (1285, 330)])
    arrow(im, [(1505, 425), (1505, 520)])
    arrow(im, [(1285, 610), (1120, 610)])
    arrow(im, [(680, 610), (515, 610)])
    arrow(im, [(295, 705), (295, 805)], both=True)
    arrow(im, [(515, 670), (595, 670), (595, 900), (680, 900)])
    ImageDraw.Draw(im).text((77, 1040), 'Due subscriptions are recorded on app open. No bank connection or background monitoring. Images are not retained.', font=font(23), fill=MUTED)
    im.save(OUT/'architecture.png', optimize=True)


def workflow():
    im = canvas(1800, 920)
    title(im, 'UPI Ledger / workflow', 'Share. Review. Save. Understand.', 'A payment stays under your control from the first import to the final category.')
    x = [75, 515, 955, 1395]
    steps = [
        ('01 / SHARE', 'Send the receipt', 'After a successful payment,\nShare to UPI Ledger.'),
        ('02 / READ', 'Extract the fields', 'OCR reads the receipt\nwithout saving an image copy.'),
        ('03 / REVIEW', 'Make it yours', 'Check amount and vendor.\nSet category and remarks.'),
        ('04 / SAVE', 'Keep the record', 'Save the fields to\nthe private local database.'),
    ]
    for xx, (tag, h, body) in zip(x, steps):
        card(im, (xx, 245, xx+330, 460), tag, h, body)
    for left, right in zip(x, x[1:]):
        arrow(im, [(left+330, 350), (right, 350)])
    cards = [
        (515, 'IF UNCLEAR', 'Enter the amount', 'Ambiguous OCR stays blank.\nReview before continuing.'),
        (955, 'YOUR CHOICE', 'Use a default or override', 'Create a vendor/category.\nChange only this payment.'),
        (1395, 'EXPLORE', 'See your spending', 'All time, by month or by week.\nFilter categories and vendors.'),
    ]
    for xx, tag, h, body in cards:
        card(im, (xx, 565, xx+330, 825), tag, h, body, '#eee9f5', '#817295')
    arrow(im, [(680, 460), (680, 565)])
    arrow(im, [(845, 665), (910, 665), (910, 490), (1120, 490), (1120, 460)], fill='#a394b4')
    arrow(im, [(1120, 460), (1120, 565)])
    arrow(im, [(1560, 460), (1560, 565)])
    paragraph(ImageDraw.Draw(im), 'Vendor defaults apply to future payments. A one-payment override does not change the default.', 80, 575, 350, 25, GREEN, False, 11)
    im.save(OUT/'workflow.png', optimize=True)


def app_preview():
    im = canvas(1600, 1100)
    shot = Image.open(OUT/'dashboard-source.jpg').convert('RGB')
    shot.thumbnail((425, 965), Image.Resampling.LANCZOS)
    x, y = 110, (1100-shot.height)//2
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((x-14, y-14, x+shot.width+14, y+shot.height+14), radius=37, fill='#234c3c')
    mask = Image.new('L', shot.size)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width, shot.height), radius=25, fill=255)
    im.paste(shot, (x, y), mask)
    d = ImageDraw.Draw(im)
    d.text((650, 180), 'UPI LEDGER', font=font(24, True), fill=GREEN)
    yy = paragraph(d, 'Your money,\nin focus.', 645, 245, 800, 76, INK, True, 10)
    yy = paragraph(d, 'A personal expense tracker for the\npayments you choose to share.', 650, yy+22, 790, 33, MUTED, False, 13)
    for text in ['On-device receipt reading', 'Custom vendors and categories', 'Weekly and monthly insights']:
        yy += 30
        d.rounded_rectangle((650, yy, 670, yy+20), radius=6, fill='#7ba68b')
        d.text((690, yy-8), text, font=font(29), fill=INK)
        yy += 31
    paragraph(d, 'Sample interface preview â€¢ synthetic spending data\nActual appearance varies by device.', 650, 945, 790, 22, MUTED, False, 9)
    im.save(OUT/'app-preview.png', optimize=True)


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    architecture()
    workflow()
    app_preview()
    print('Rendered architecture.png, workflow.png and app-preview.png')
