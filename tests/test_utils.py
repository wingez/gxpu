from gcpu import utils


def test_split_many():
    assert utils.split_many('hej', ['test']) == ['hej']
    assert utils.split_many('hej', ['e']) == ['h', 'j']

    assert utils.split_many('1,2 3,4&7', [' ', ',']) == ['1', '2', '3', '4&7']
